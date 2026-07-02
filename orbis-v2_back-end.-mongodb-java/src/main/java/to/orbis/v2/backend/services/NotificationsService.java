package to.orbis.v2.backend.services;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.types.ObjectId;
import org.geotools.referencing.GeodeticCalculator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import to.orbis.v2.backend.exceptions.UnknownUserException;
import to.orbis.v2.backend.models.*;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.repositories.*;

import java.io.StringWriter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationsService {

    FollowsAggregationRepository followsAggregationRepository;
    FollowsRepository followsRepository;
    MustacheFactory mustacheFactory;
    NotificationsRepository notificationsRepository;
    NotificationsAggregationsRepository notificationsAggregationsRepository;
    UsersRepository usersRepository;
    UsersAggregationsRepository usersAggregationsRepository;

    CommentsAggregationRepository commentsAggregationRepository;
    PostsAggregationsRepository postsAggregationsRepository;
    GroupsRepository groupsRepository;

    ExecutorService notificationExecutor = Executors.newFixedThreadPool(20);

    public Mono<Void> notifyPostCreated(ExtendedPost post) {
        val fromUserMono = usersRepository.findOneByUserKey(post.getUserKey());

        return fromUserMono.flatMapMany(fromUser -> followsAggregationRepository
                        .findAllToNotify(post).distinct()
                        .flatMap(user -> {

                            if (fromUser.getBlockedBy().contains(user.getUserKey())) {
                                return Mono.empty();
                            }

                            if (!checkNotifyConditions(post, user)) {
                                return Mono.empty();
                            }

                            val title = String.format("%s", post.getTitle() == null ? "New post" : post.getTitle());
                            val body = formatBody(post, getUserLanguage(user));
                            val notificationType = determineType(post);

                            val notification = createNotification(title, body, notificationType);
                            notification.setPostKey(post.getPostKey());
                            notification.setFromUserKey(post.getUserKey());
                            notification.setForUserKey(user.getUserKey());

                            var builder = Message.builder()
                                    .setNotification(Notification.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build());

                            builder = builder.putData("type", notificationType.name())
                                    .putData("fromUserKey", post.getUserKey());


                            if (post.getPlaceKey() != null) {
                                notification.setPlaceKey(post.getPlaceKey());
                            }

                            if (notificationType == NotificationType.CHECK_IN) {
                                builder = builder.putData("contentKey", post.getPlaceKey())
                                        .putData("postKey", post.getPostKey());
                            } else if (notificationType == NotificationType.POST) {
                                builder = builder.putData("contentKey", post.getPostKey());
                            } else {
                                log.error("Wrong notification type for {}", post.getPostKey());
                                builder = builder.putData("contentKey", post.getPostKey());
                            }

                            if (post.getGroupKey() != null) {
                                notification.setGroupKey(post.getGroupKey());
                            }

                            sendToUser(user, builder, "Failed to notify {} about {}", post.getPostKey());
                            return Mono.just(notification);
                        }))
                .buffer(20)
                .flatMap(notificationsRepository::saveAll)
                .then();
    }

    private void sendToUser(User user, Message.Builder builder, String logTemplate, String extraDetails) {
        if (user.getFcmTokens() == null || user.getFcmTokens().isEmpty()) {
            log.debug("user {} getFcmTokens {} is empty or null", user.getUserKey(), user.getFcmTokens());
            return;
        }

        // Keep an ordered token list so it aligns with FirebaseMessaging's per-message responses.
        val tokens = new ArrayList<>(user.getFcmTokens());
        val messages = tokens.stream().map(token -> builder
                .setAndroidConfig(AndroidConfig.builder()
                        .setNotification(
                                AndroidNotification.builder()
                                        .setSound("default")
                                        .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build())
                .setToken(token)
                .build()).collect(Collectors.toList());

        notificationExecutor.execute(() -> sendAndForget(user, logTemplate, extraDetails, tokens, messages));
    }

    private void sendAndForget(User user, String logTemplate, String extraDetails, List<String> tokens, List<Message> messages) {
        var success = 0;
        var failure = 0;
        for (int i = 0; i < messages.size(); i++) {
            try {
                FirebaseMessaging.getInstance().send(messages.get(i));
                success++;
            } catch (FirebaseMessagingException ex) {
                failure++;
                val errorCode = ex.getMessagingErrorCode();
                log.debug("FCM send failed for user {} message {}: errorCode={} message={}",
                        user.getUserKey(), i, errorCode, ex.getMessage());
                // Permanently invalid tokens never recover - prune them so we stop sending to them.
                if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    val deadToken = tokens.get(i);
                    usersAggregationsRepository.deleteFcmToken(user.getUserKey(), deadToken)
                            .subscribe(_unused -> {},
                                    err -> log.warn("Failed to prune dead fcm token for user {}", user.getUserKey(), err));
                }
            } catch (Exception ex) {
                failure++;
                log.error(logTemplate, user.getUserKey(), extraDetails, ex);
            }
        }
        log.debug("User {} send {} success notifications and {} failure notifications", user.getDisplayName(), success, failure);
    }

    private NotificationType determineType(ExtendedPost post) {
        if (post.getType() == PostType.CHECK_IN) {
            return NotificationType.CHECK_IN;
        }
        return NotificationType.POST;
    }

    private boolean checkNotifyConditions(ExtendedPost post, User user) {
        return post.getType() != PostType.CHECK_IN || distanceBetween(post, user) < 50.0;
    }

    private double distanceBetween(ExtendedPost post, User user) {
        if (post.getCoordinates() == null || user.getCoordinates() == null) {
            return 10000;
        }

        val geodeticCalc = new GeodeticCalculator();
        geodeticCalc.setStartingGeographicPoint(post.getCoordinates().getX(), post.getCoordinates().getY());
        geodeticCalc.setDestinationGeographicPoint(user.getCoordinates().getX(), user.getCoordinates().getY());
        return geodeticCalc.getOrthodromicDistance() / 1000;
    }

    private String formatBody(ExtendedPost post, Language language) {
        Mustache template;
        try {
            template = mustacheFactory.compile(
                    String.format(
                            "messageTemplates/%s/%s.mustache",
                            language.name().toLowerCase(),
                            post.getType().name().toLowerCase()
                    )
            );
        } catch (Exception ex) {
            log.error("Failed to load custom message template for {} post. Falling back to default", post.getPostKey(), ex);
            template = mustacheFactory.compile(
                    String.format(
                            "messageTemplates/%s/text.mustache",
                            language.name().toLowerCase()
                    )
            );
        }

        StringWriter sw = new StringWriter();

        try {
            template.execute(sw, post).flush();
        } catch (Exception ex) {
            log.error("Failed to execute template for {} post.", post.getPostKey(), ex);
            return String.format("%s%s%s: %s",
                    Optional.ofNullable(post.getUser()).map(SimplifiedUser::getDisplayName).orElse("Unknown"),
                    Optional.ofNullable(post.getGroup()).map(simplifiedGroup -> "(" + simplifiedGroup.getName() + ")").orElse(""),
                    Optional.ofNullable(post.getPlace()).map(place -> " at " + place.getName()).orElse(""),
                    post.getDetails());
        }

        return sw.toString();
    }

    public Mono<Void> notifyNewFollower(User userToFollow, User followerUser) {
        if (followerUser.getBlockedBy().contains(userToFollow.getUserKey())) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
                    val title = "New follower";
                    val body = formatBody(userToFollow, followerUser, getUserLanguage(userToFollow));
                    val notificationType = NotificationType.FOLLOWER;

                    val notification = createNotification(title, body, notificationType);

                    notification.setFromUserKey(followerUser.getUserKey());
                    notification.setForUserKey(userToFollow.getUserKey());


                    var builder = Message.builder()
                            .setNotification(Notification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build());

                    builder = builder.putData("fromUserKey", followerUser.getUserKey())
                            .putData("type", notificationType.name())
                            .putData("contentKey", followerUser.getUserKey());

                    sendToUser(userToFollow, builder, "Failed to notify {} about new follower {}", followerUser.getUserKey());

                    return notification;
                })
                .flatMap(notificationsRepository::save)
                .then();
    }

    private to.orbis.v2.backend.models.entity.Notification createNotification(String title, String body, NotificationType notificationType) {
        val notification = new to.orbis.v2.backend.models.entity.Notification();

        notification.setTitle(title);
        notification.setDetails(body);
        notification.setType(notificationType);

        notification.setId(new ObjectId());
        notification.setNotificationKey(notification.getId().toString());
        notification.setTimestamp(Instant.now());
        notification.setSeen(false);

        return notification;
    }

    private String formatBody(User userToFollow, User followerUser, Language language) {
        Mustache template;
        try {
            template = mustacheFactory.compile(
                    String.format(
                            "messageTemplates/%s/follower.mustache",
                            language.name().toLowerCase()
                    )
            );
        } catch (Exception ex) {
            log.error("Failed to load custom message template for new follower. Falling back to default", ex);
            return String.format("%s follows you", followerUser.getDisplayName());
        }

        StringWriter sw = new StringWriter();

        val context = new HashMap<String, User>();
        context.put("userToFollow", userToFollow);
        context.put("followerUser", followerUser);

        try {
            template.execute(sw, context).flush();
        } catch (Exception ex) {
            log.error("Failed to execute template for new following user {} which started to follow {} user.",
                    followerUser.getUserKey(), userToFollow.getUserKey(), ex);

            return String.format("%s follows you", followerUser.getDisplayName());
        }

        return sw.toString();
    }

    private String formatBodyRequest(User userToFollow, User followerUser, Language language) {
        Mustache template;
        try {
            template = mustacheFactory.compile(
                    String.format(
                            "messageTemplates/%s/followRequest.mustache",
                            language.name().toLowerCase()
                    )
            );
        } catch (Exception ex) {
            log.error("Failed to load custom message template for new follow request. Falling back to default", ex);
            return String.format("%s wants to follow you", followerUser.getDisplayName());
        }

        StringWriter sw = new StringWriter();

        val context = new HashMap<String, User>();
        context.put("userToFollow", userToFollow);
        context.put("followerUser", followerUser);

        try {
            template.execute(sw, context).flush();
        } catch (Exception ex) {
            log.error("Failed to execute template for new follow request from user {} which wants to follow {} user.",
                    followerUser.getUserKey(), userToFollow.getUserKey(), ex);

            return String.format("%s wants to follow you", followerUser.getDisplayName());
        }

        return sw.toString();
    }

    public Flux<ExtendedNotification> getNotifications(String userKey, Pageable pageable) {
        return notificationsAggregationsRepository.findNotificationsForUser(userKey, pageable);
    }

    public Mono<Tuple2<Long, Long>> getUnreadNotificationsCount(String userKey) {
        return notificationsRepository.countByForUserKeyAndSeen(userKey, false)
                .switchIfEmpty(Mono.just(0L))
                .flatMap(notificationsCount -> followsRepository.countByUserKeyAndAcceptedFalseAndSeenFalse(userKey)
                        .switchIfEmpty(Mono.just(0L))
                        .map(followRequestCount -> Tuples.of(notificationsCount, followRequestCount)));
    }

    public Mono<Void> setSeenStatus(List<String> notificationKeys, String userKey) {
        return notificationsAggregationsRepository.setSeenStatus(notificationKeys, userKey);
    }

    public Mono<Void> sendChatNotification(String fromUserKey, String toUserKey, String message, String chatType, String mediaUrl, String conversationId) {
        return usersRepository.findOneByUserKey(fromUserKey)
                .flatMap(fromUser -> usersRepository.findOneByUserKey(toUserKey)
                        .flatMap(toUser -> {

                            if (fromUser.getBlockedBy().contains(toUserKey)) {
                                return Mono.empty();
                            }

                            val title = fromUser.getDisplayName() == null ? "Unnamed" : fromUser.getDisplayName();
                            val body = formatChatBody(fromUser, toUser, message, chatType);
                            val notificationType = NotificationType.MESSAGE;

                            val notification = createNotification(title, body, notificationType);

                            notification.setFromUserKey(fromUser.getUserKey());
                            notification.setForUserKey(toUser.getUserKey());


                            var builder = Message.builder()
                                    .setNotification(Notification.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build());

                            builder = builder.putData("fromUserKey", fromUser.getUserKey())
                                    .putData("type", notificationType.name())
                                    .putData("contentKey", conversationId);

                            sendToUser(toUser, builder, "Failed to notify {} about new chat message {}", fromUser.getUserKey());

                            return Mono.just(notification);
                        }))
//                .flatMap(notificationsRepository::save)
                .switchIfEmpty(Mono.error(UnknownUserException::new))
                .then();
    }

    private String formatChatBody(User fromUser, User toUser, String message, String chatType) {
        if (Objects.equals(chatType, "TEXT")) {
            return message;
        }
        return String.format("sent %s", chatType.toLowerCase());
    }

    public Mono<Void> notifyNewComment(Comment comment) {
        return ((!comment.isReply())
                ? findUserForComment(comment)
                : findUserForReply(comment))
                // do not notify myself
                .filter(u -> !u.getUserKey().equals(comment.getUserKey()))
                .flatMap(toUser -> usersRepository.findOneByUserKey(comment.getUserKey())
                        .flatMap(fromUser -> {

                            if (fromUser.getBlockedBy().contains(toUser.getUserKey())) {
                                return Mono.empty();
                            }

                            log.debug("Going to notify: {} about comment {}", toUser.getUserKey(), comment.getCommentKey());

                            val title = (fromUser.getDisplayName() == null ? "Unnamed" : fromUser.getDisplayName()) + " commented";
                            val body = formatCommentBody(fromUser, toUser, comment, getUserLanguage(toUser));
                            val notificationType = NotificationType.COMMENT;

                            val notification = createNotification(title, body, notificationType);

                            notification.setFromUserKey(fromUser.getUserKey());
                            notification.setForUserKey(toUser.getUserKey());
                            notification.setCommentKey(comment.getCommentKey());
                            notification.setPostKey(comment.getPostKey());

                            var builder = Message.builder()
                                    .setNotification(Notification.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build());

                            builder = builder.putData("fromUserKey", fromUser.getUserKey())
                                    .putData("type", notificationType.name())
                                    .putData("contentKey", comment.getPostKey());

                            sendToUser(toUser, builder, "Failed to notify {} about new comment {}", fromUser.getUserKey());

                            return Mono.just(notification);

                        }))
                .flatMap(notificationsRepository::save)
                .then();
    }

    private String formatCommentBody(User fromUser, User toUser, Comment comment, Language language) {
        Mustache template;
        try {
            template = mustacheFactory.compile(
                    String.format(
                            "messageTemplates/%s/%s.mustache",
                            language.name().toLowerCase(),
                            comment.isReply() ? "reply" : "comment"
                    )
            );
        } catch (Exception ex) {
            log.error("Failed to load custom message template for {} post. Falling back to default", comment.getPostKey(), ex);
            return String.format("%s has replied to your %s", fromUser.getDisplayName(), comment.isReply() ? "comment" : "post");
        }

        StringWriter sw = new StringWriter();

        try {
            template.execute(sw, fromUser).flush();
        } catch (Exception ex) {
            log.error("Failed to execute template for {} comment.", comment.getCommentKey(), ex);
            return String.format("%s has replied to your %s", fromUser.getDisplayName(), comment.isReply() ? "comment" : "post");
        }

        return sw.toString();
    }

    private Mono<User> findUserForReply(Comment comment) {
        return commentsAggregationRepository.findCommentAuthor(comment.getReplyToKey());
    }

    private Mono<User> findUserForComment(Comment comment) {
        return postsAggregationsRepository.findPostAuthor(comment.getPostKey());
    }

    public Mono<Void> notifyNewFollowRequest(User userToFollow, User followerUser) {

        if (followerUser.getBlockedBy().contains(userToFollow.getUserKey())) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
                    val title = "New follow request";
                    val body = formatBodyRequest(userToFollow, followerUser, getUserLanguage(userToFollow));
                    val notificationType = NotificationType.FOLLOW_REQUEST;

                    var builder = Message.builder()
                            .setNotification(Notification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build());

                    builder = builder.putData("fromUserKey", followerUser.getUserKey())
                            .putData("type", notificationType.name())
                            .putData("contentKey", followerUser.getUserKey());

                    sendToUser(userToFollow, builder, "Failed to notify {} about new follow request {}", followerUser.getUserKey());

                    return "success";
                })
                .then();
    }

    public Mono<Void> notifySubscription(
            StripeSubscriptionEventType type,
            Subscription subscription,
            Group group,
            User user,
            User receiver,
            NotificationType notificationType
    ) {
        return Mono.defer(() -> {
                    val title = getSubscriptionTitle(type);
                    val body = getSubscriptionBody(type, subscription, group, user, getUserLanguage(receiver), notificationType);

                    var builder = Message.builder()
                            .setNotification(Notification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build());

                    builder = builder.putData("fromUserKey", user.getUserKey())
                            .putData("type", notificationType.name())
                            .putData("contentKey", subscription.getSubscriptionKey());

                    sendToUser(receiver, builder, "Failed to notify {} about created/deleted subscription {}", subscription.getName());

                    val notification = createNotification(title, body, notificationType);

                    notification.setFromUserKey(user.getUserKey());
                    notification.setForUserKey(receiver.getUserKey());
                    notification.setGroupKey(group.getGroupKey());

                    return Mono.just(notification);
                })
                .flatMap(notificationsRepository::save)
                .then();
    }

    public Mono<Void> deleteNotification(String notificationKey, String userKey) {
        return notificationsRepository.deleteAllByNotificationKeyAndForUserKey(notificationKey, userKey);
    }

    public Mono<String> reportPost(String title, String body, ExtendedPost post, Optional<User> user) {
        return Flux.concat(
                        usersRepository.findAllBySuperAdminTrue().map(User::getUserKey),
                        Mono.justOrEmpty(post.getGroupKey()).flatMapMany(groupsRepository::findOneByGroupKeyAndDeletedFalse)
                                .flatMapIterable(Group::getAdmins))
                .distinct()
                .flatMap(usersRepository::findOneByUserKey)
                .flatMap(admins -> sendReportNotifications(admins, user, title, body, post.getPostKey(), NotificationType.REPORT_POST))
                .then(Mono.just("Post reported"));
    }

    public Mono<String> reportGroup(String title, String body, ExtendedGroup group, Optional<User> user) {
        return Flux.concat(
                        usersRepository.findAllBySuperAdminTrue().map(User::getUserKey),
                        Mono.justOrEmpty(group.getGroupKey()).flatMapMany(groupsRepository::findOneByGroupKeyAndDeletedFalse)
                                .flatMapIterable(Group::getAdmins))
                .distinct()
                .flatMap(usersRepository::findOneByUserKey)
                .flatMap(admins -> sendReportNotifications(admins, user, title, body, group.getGroupKey(), NotificationType.REPORT_GROUP))
                .then(Mono.just("Group reported"));
    }

    private Mono<String> sendReportNotifications(User admin, Optional<User> user, String title, String body, String key, NotificationType type) {

        log.debug("Going to notify: {} about {} {}", admin.getUserKey(), type, key);

        val notification = createNotification(title, body, type);

        user.ifPresent(u -> notification.setFromUserKey(u.getUserKey()));
        notification.setForUserKey(admin.getUserKey());

        switch (type) {

            case POST:
            case COMMENT:
            case CHECK_IN:
            case FOLLOWER:
            case FOLLOW_REQUEST:
            case MESSAGE:
            case REPORT_POST:
                notification.setPostKey(key);
                break;
            case REPORT_GROUP:
                notification.setGroupKey(key);
                break;
            case REPORT_USER:
                notification.setFromUserKey(key);
                break;
            case REPORT_PLACE:
                notification.setPlaceKey(key);
                break;
        }

        var builder = Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        builder = builder.putData("type", type.name())
                .putData("contentKey", key);

        Message.Builder finalBuilder = builder;
        builder = user.map(u -> finalBuilder.putData("fromUserKey", u.getUserKey())).orElse(builder);

        sendToUser(admin, builder, "Failed to notify {} about new report {}", key);

        return notificationsRepository.save(notification)
                .then(Mono.just("Saved"));
    }

    public Mono<String> reportUser(String title, String body, ExtendedUser u, Optional<User> reportingUser) {
        return usersRepository.findAllBySuperAdminTrue()
                .flatMap(admins -> sendReportNotifications(admins, reportingUser, title, body, u.getUserKey(), NotificationType.REPORT_USER))
                .then(Mono.just("User reported"));
    }

    public Mono<String> reportPlace(String title, String body, ExtendedPlace place, Optional<User> user) {
        return usersRepository.findAllBySuperAdminTrue()
                .flatMap(admins -> sendReportNotifications(admins, user, title, body, place.getPlaceKey(), NotificationType.REPORT_PLACE))
                .then(Mono.just("Place reported"));
    }

    private Language getUserLanguage(User user) {
        if (user.getLanguage() == null) {
            return Language.EN;
        } else {
            return Language.get(user.getLanguage());
        }
    }

    private String getSubscriptionTitle(StripeSubscriptionEventType type) {
        switch (type) {
            case CREATED:
                return "New subscription created";
            case DELETED:
                return "Subscription deleted";
            case PAYMENT_PROBLEM:
                return "Subscription payment problem";
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    private String getSubscriptionBody(
            StripeSubscriptionEventType type,
            Subscription subscription,
            Group group,
            User user,
            Language language,
            NotificationType notificationType
    ) {
        Mustache template;
        try {
            template = mustacheFactory.compile(
                    String.format(
                            "messageTemplates/%s/subscription/%s_%s.mustache",
                            language.name().toLowerCase(),
                            notificationType == NotificationType.SUBSCRIPTION_MAIN_USER ? "mainUser" : "endUser",
                            type.name().toLowerCase()
                    )
            );
        } catch (Exception ex) {
            log.error("Failed to load custom message template for subscription. type {}. Falling back to default", notificationType, ex);
            return String.format("%s event with your subscription %s", type.name().toLowerCase(), subscription.getName());
        }

        StringWriter sw = new StringWriter();

        var subscriptionNotification = new SubscriptionNotification(user.getDisplayName(), subscription.getName(), group.getName());

        try {
            template.execute(sw, subscriptionNotification).flush();
        } catch (Exception ex) {
            log.error("Failed to load custom message template for subscription. type {}. Falling back to default", notificationType, ex);

            return String.format("%s event with your subscription %s", type.name().toLowerCase(), subscription.getName());
        }

        return sw.toString();
    }
}
