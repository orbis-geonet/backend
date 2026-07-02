package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.InstagramNotConnected;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.UserExistsException;
import to.orbis.v2.backend.mappers.UserMapper;
import to.orbis.v2.backend.models.IgStatus;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.UserPictureType;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.models.requests.users.UserDetailsRequest;
import to.orbis.v2.backend.repositories.*;
import to.orbis.v2.backend.utils.OrbisBeanUtils;
import to.orbis.v2.backend.utils.SlugUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsersService {

    UsersRepository usersRepository;
    UsersAggregationsRepository usersAggregationsRepository;
    GroupsAggregationsRepository groupsAggregationsRepository;
    FirebaseAuthService firebaseAuthService;
    UserPictureRepository userPictureRepository;
    IgService igService;
    FollowsRepository followsRepository;
    ShortLinksService shortLinksService;
    UserMapper userMapper;
    NotificationsService notificationsService;

    public Mono<ExtendedUser> getExtendedUser(String userKey, Optional<String> viewerUserKey) {
        return usersAggregationsRepository.findOneByUserKey(userKey, viewerUserKey);
    }

    public Flux<ExtendedUser> findExtendedUsers(String search, Optional<String> viewerUserKey, Pageable pageable) {
        return usersAggregationsRepository.findByDisplayName(search, viewerUserKey, pageable);
    }

    public Mono<User> defaultUserFromPrincipal(JwtAuthenticationToken principal) {
        return Mono.just(principal).map(this::convertToUser);
    }

    public Mono<User> save(User user) {
        return usersRepository.save(user);
    }

    private User convertToUser(JwtAuthenticationToken authentication) {
        return User.builder()
                .userKey(authentication.getName())
                .activeServerTimestamp(Instant.now())
                .timestamp(Instant.now())
                .deleted(false)
                .superAdmin(false)
                .email(String.valueOf(authentication.getTokenAttributes().get("email")))
                .build();
    }

    public Mono<ExtendedUser> updateDetails(User incomingUser, JwtAuthenticationToken principal, UserDetailsRequest userDetailsRequest) {
        return usersRepository.findOneByUserKey(principal.getName())
                .switchIfEmpty(defaultUserFromPrincipal(principal))
                .map(existingUser -> {
                    final User updatedUser = updateFields(existingUser, incomingUser);
                    if (userDetailsRequest.getAccountPrivate() != null) {
                        updatedUser.setAccountPrivate(userDetailsRequest.getAccountPrivate());
                    }
                    return updatedUser;
                })
                .flatMap(u -> {
                            var emptySlug = SlugUtils.createEmptySlug(u.getDisplayName());
                            return usersRepository.countByEmptySlug(emptySlug)
                                    .flatMap(count -> {
                                        var slug = SlugUtils.getSlugNames(emptySlug, u.getDisplayName(), count);
                                        u.setSlug(slug);
                                        u.setEmptySlug(emptySlug);
                                        return shortLinksService.generateShortGroupLink(slug, u.getDisplayName(), null);
                                    })
                                    .flatMap(sl -> {
                                        u.setShareLink(sl.getShortLink());
                                        u.setFullShareLink(sl.getFullLink());
                                        return usersRepository.save(u);
                                    })
                                    .switchIfEmpty(Mono.just(u));
                        }
                )
                .flatMap(usersRepository::save)
                .flatMap(u -> getExtendedUser(u.getUserKey(), Optional.of(u.getUserKey())));
    }

    private User updateFields(User existingUser, User incomingUser) {
        OrbisBeanUtils.copyNotNullPropertiesSkipping(
                User.class,
                existingUser,
                incomingUser,
                User.Fields.timestamp, User.Fields.activeServerTimestamp, User.Fields.accountPrivate);
        if (existingUser.getCreateTimestamp() == null) {
            existingUser.setCreateTimestamp(Instant.now());
        }
        existingUser.setTimestamp(Instant.now());
        return existingUser;
    }

    public Mono<User> authenticateUser(String email, String password) {
        return usersRepository.findOneByEmailAndDeletedFalse(email)
                .switchIfEmpty(Mono.error(new AuthenticationCredentialsNotFoundException("Invalid credentials")))
                .flatMap(user -> firebaseAuthService
                        .authenticate(user.getEmail(), password)
                        .flatMap(token -> Mono.just(user)
                                .map(u -> u.setTokens(token))));
    }

    public Mono<User> registerUser(User user, String password) {
        return usersRepository.findOneByEmailAndDeletedFalse(user.getEmail())
                .flatMap(u -> Mono.<User>error(new UserExistsException()))
                .switchIfEmpty(firebaseAuthService.signup(user, password)
                        .flatMap(u -> {
                            var emptySlug = SlugUtils.createEmptySlug(u.getDisplayName());
                            return usersRepository.countByEmptySlug(emptySlug)
                                    .flatMap(count -> {
                                        var slug = SlugUtils.getSlugNames(emptySlug, u.getDisplayName(), count);
                                        u.setSlug(slug);
                                        u.setEmptySlug(emptySlug);
                                        return shortLinksService.generateShortGroupLink(slug, u.getDisplayName(), null);
                                    })
                                    .flatMap(sl -> {
                                        u.setShareLink(sl.getShortLink());
                                        u.setFullShareLink(sl.getFullLink());
                                        return usersRepository.save(u);
                                    });
                        })
                        // if user is already present in firebase - try to authenticate
                        .onErrorResume(th -> th.getMessage().contains("EMAIL_EXISTS"),
                                th -> authenticateUser(user.getEmail(), password)
                                        .flatMap(authenticated -> {
                                            val merged = updateFields(authenticated, user);
                                            return usersRepository.save(merged);
                                        })));
    }

    public Mono<Void> deleteUser(String userKey) {
        return usersRepository.findOneByUserKey(userKey)
                .map(u -> u.setDeleted(true))
                .flatMap(usersRepository::save)
                .then(firebaseAuthService.deleteUser(userKey));
    }

    Mono<User> findUser(String userKey, String message) {
        return usersRepository.findOneByUserKey(userKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException(message)));
    }

    public Flux<UserPicture> getUserPictures(String userKey, Optional<String> viewerKey, Pageable pageable) {
        return checkFollower(userKey, viewerKey)
                .flatMapMany(_ignored -> userPictureRepository.findByUserKeyAndTypeOrderByTimestampDesc(userKey,
                        UserPictureType.ORBIS, pageable));
    }

    public Mono<UserPicture> addUserPicture(UserPicture userPicture) {
        val id = new ObjectId();
        return userPictureRepository.save(userPicture.setPictureKey(id.toHexString()).setId(id).setType(UserPictureType.ORBIS));
    }

    public Mono<Void> deleteUserPicture(String pictureKey) {
        return userPictureRepository.deleteByPictureKey(pictureKey);
    }

    public Flux<UserPicture> getIgUserPictures(String userKey, Optional<String> viewerUserKey, PageRequest pageable) {
        return checkFollower(userKey, viewerUserKey)
                .flatMapMany(_ignored ->
                        userPictureRepository.findByUserKeyAndTypeOrderByTimestampDesc(userKey, UserPictureType.INSTAGRAM, pageable)
                                .flatMapSequential(userPicture -> refreshImageLink(userKey, userPicture))
                                .switchIfEmpty((pageable.getPageNumber() == 0)
                                        ? (importInstagramPhotos(userKey)
                                           .sort(Comparator.comparing(UserPicture::getTimestamp).reversed())
                                           .take(pageable.getPageSize()))
                                        : Flux.empty()));
    }

    private Mono<UserPicture> refreshImageLink(String userKey, UserPicture userPicture) {
        if (userPicture.getImageType() != PostType.IMAGE) {
            return Mono.just(userPicture);
        }

        if (userPicture.getLoadTimestamp() != null
                && userPicture.getLoadTimestamp().plus(1, ChronoUnit.DAYS).isAfter(Instant.now())) {
            return Mono.just(userPicture);
        }

        if (stillValidAccordingToUrl(userPicture)) {
            return Mono.just(userPicture);
        }

        return igService.refreshLinks(userKey, userPicture);
    }

    private boolean stillValidAccordingToUrl(UserPicture userPicture) {
        if (userPicture.getPictureUrl().isEmpty()) {
            return false;
        }
        val url = userPicture.getPictureUrl().get(0);
        val params = UriComponentsBuilder.fromHttpUrl(url).build().getQueryParams();

        if (!params.containsKey("oe")) {
            return false;
        }

        val expString = params.get("oe").get(0);

        val expTimestamp = Long.valueOf(expString, 16);

        val expTime = Instant.ofEpochSecond(expTimestamp);

        return expTime.isAfter(Instant.now().plus(1, ChronoUnit.HOURS));
    }

    public Flux<UserPicture> importInstagramPhotos(String userKey) {
        return igService.connect(userKey)
                .filter(igLink -> igLink.getStatus() == IgStatus.CONNECTED)
                .switchIfEmpty(Mono.error(InstagramNotConnected::new))
                .flatMapMany(igService::fillMedia)
                .map(up -> up.setPictureKey(up.getId().toHexString()).setUserKey(userKey))
                .buffer()
                .map(saving -> {
                    saving.stream().map(UserPicture::getIgMediaId).forEach(id -> log.info("Saving: {}", id));
                    return saving;
                })
                .flatMap(userPictureRepository::saveAll);
    }

    public Flux<SimplifiedGroup> getAdminGroups(String userKey, Optional<String> viewerKey, Optional<GeoJsonPoint> viewerLocation, Pageable pageable) {
        return checkFollower(userKey, viewerKey)
                .flatMap(_ignored -> locateUser(viewerKey, viewerLocation))
                .flatMapMany(vl -> groupsAggregationsRepository.findGroupsWithUser(userKey, Group.Fields.admins, vl, pageable));
    }

    private Mono<Optional<GeoJsonPoint>> locateUser(Optional<String> viewerKey, Optional<GeoJsonPoint> viewerLocation) {
        return viewerLocation.map(Mono::just)
                .orElse(viewerKey.map(uk -> usersRepository.findOneByUserKey(uk).map(User::getCoordinates)).orElse(Mono.empty()))
                .map(Optional::of)
                .switchIfEmpty(Mono.just(Optional.empty()));
    }

    public Flux<SimplifiedGroup> getMemberGroups(String userKey, Optional<String> viewerKey, Optional<GeoJsonPoint> viewerLocation, Pageable pageable) {
        return checkFollower(userKey, viewerKey)
                .flatMap(_ignored -> locateUser(viewerKey, viewerLocation))
                .flatMapMany(vl -> groupsAggregationsRepository.findGroupsWithUser(userKey, Group.Fields.members, vl, pageable));
    }

    public Mono<Boolean> checkFollower(String userKey, Optional<String> viewerKey) {
        return usersRepository.findOneByUserKey(userKey)
                .flatMap(u -> {
                    if (!u.isAccountPrivate()) {
                        return Mono.just(true);
                    }

                    final Supplier<Throwable> errorSupplier = () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is private. Request to follow");
                    if (viewerKey.isEmpty()) {
                        return Mono.error(errorSupplier);
                    }

                    val vk = viewerKey.get();

                    if (vk.equals(userKey)) {
                        return Mono.just(true);
                    }

                    return followsRepository.findFirstByFollowerKeyAndUserKey(vk, userKey)
                            .filter(Follow::isAccepted)
                            .map(Follow::isAccepted)
                            .switchIfEmpty(Mono.error(errorSupplier));
                });
    }

    public Flux<SimplifiedGroup> getFollowerGroups(String userKey, Optional<String> viewerKey, Optional<GeoJsonPoint> viewerLocation, Pageable pageable) {
        return checkFollower(userKey, viewerKey)
                .flatMap(_ignored -> locateUser(viewerKey, viewerLocation))
                .flatMapMany(vl -> groupsAggregationsRepository.findGroupsWithUser(userKey, Group.Fields.followers, vl, pageable));
    }

    public Mono<Void> updateLocation(String userKey, GeoJsonPoint point) {
        return usersAggregationsRepository.updateLocation(userKey, point);
    }

    public Mono<Void> addFcmToken(String userKey, String fcmToken) {
        return usersAggregationsRepository.addFcmToken(userKey, sanitizeFcmToken(fcmToken));
    }

    static String sanitizeFcmToken(String raw) {
        if (raw == null) {
            return null;
        }
        var token = raw.trim();
        for (val prefix : List.of("fcm-token=", "fcmtoken=", "token=")) {
            if (token.regionMatches(true, 0, prefix, 0, prefix.length())) {
                token = token.substring(prefix.length());
                break;
            }
        }
        // Undo percent-encoding of ':' / '/' / '+' without URLDecoder's '+'-becomes-space behaviour.
        if (token.indexOf('%') >= 0) {
            token = token.replace("%3A", ":").replace("%3a", ":")
                    .replace("%2F", "/").replace("%2f", "/")
                    .replace("%2B", "+").replace("%2b", "+");
        }
        return token;
    }

    public Mono<Void> deleteFcmToken(String userKey, String fcmToken) {
        return usersAggregationsRepository.deleteFcmToken(userKey, fcmToken);
    }

    public Flux<ChatUser> lookupChatUsers(List<String> userKeys, String viewingUserKey) {
        return usersAggregationsRepository.lookupChatUsers(userKeys, viewingUserKey);
    }

    public Mono<String> reportUser(String userKey, String reason, Optional<String> reportingUser) {
        return usersAggregationsRepository.findOneByUserKey(userKey, reportingUser)
                .flatMap(u -> reportingUser.map(usersRepository::findOneByUserKey).orElse(Mono.empty()).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
                        .flatMap(ru -> {
                            if (u.isReported()) {
                                return Mono.just("User is already reported");
                            }

                            return usersAggregationsRepository.setReported(u.getUserKey(), reason)
                                    .flatMap(_ignored -> sendReport(u, reason, ru));
                        }));

    }

    private Mono<String> sendReport(ExtendedUser user, String reason, Optional<User> reportingUser) {
        var originalUser = userMapper.extendedUserToUser(user);
        var emptySlug = SlugUtils.createEmptySlug(originalUser.getDisplayName());

        Mono<String> shortLink = (originalUser.getShareLink() == null || originalUser.getShareLink().isEmpty())
                ? usersRepository.countByEmptySlug(emptySlug)
                  .flatMap(count -> {
                      var slug = SlugUtils.getSlugNames(emptySlug, originalUser.getUserKey(), count);
                      originalUser.setSlug(slug);
                      originalUser.setEmptySlug(emptySlug);
                      return shortLinksService.generateShortGroupLink(slug, originalUser.getDisplayName(), null);
                  })
                  .flatMap(sl -> {
                      originalUser.setShareLink(sl.getShortLink());
                      originalUser.setFullShareLink(sl.getFullLink());
                      return usersRepository.save(originalUser);
                  })
                  .flatMap(sl -> Mono.just(sl.getShareLink()))
                : Mono.just(originalUser.getShareLink());

        return shortLink.flatMap(sl -> {
            val title = String.format("User '%s' was reported", user.getDisplayName());

            var body = "Reason: " + reason + "\n" +
                    "User details:\n";

            body += "Open in app: " + sl + "\n";
            body += "\n";

            if (reportingUser.isPresent()) {
                body += "User reported: \n";
                body += "    " + reportingUser.get().getDisplayName() + "\n";
            }

            return notificationsService.reportUser(title, body, user, reportingUser).map(_ignored -> "User was reported");
        });
    }

    public Mono<String> shareUser(String userKey) {
        return usersRepository.findOneByUserKey(userKey)
                .flatMap(user -> {
                    if ((user.getShareLink() == null || user.getShareLink().isBlank())) {
                        var emptySlug = SlugUtils.createEmptySlug(user.getDisplayName());
                        return usersRepository.countByEmptySlug(emptySlug)
                                .flatMap(count -> {
                                    var slug = SlugUtils.getSlugNames(emptySlug, user.getDisplayName(), count);
                                    user.setSlug(slug);
                                    user.setEmptySlug(emptySlug);
                                    return shortLinksService.generateShortGroupLink(slug, user.getDisplayName(), null);
                                })
                                .flatMap(sl -> {
                                    user.setShareLink(sl.getShortLink());
                                    user.setFullShareLink(sl.getFullLink());
                                    return usersRepository.save(user)
                                            .flatMap(it -> Mono.just(it.getShareLink()));
                                });
                    } else {
                        return Mono.just(user.getShareLink());
                    }
                });

    }

    public Mono<Void> blockUser(String userKey, String blockingUser) {
        return usersAggregationsRepository.blockUser(userKey, blockingUser);
    }

    public Mono<Void> unblockUser(String userKey, String blockingUser) {
        return usersAggregationsRepository.unblockUser(userKey, blockingUser);
    }

    public Flux<ExtendedUser> getBlockedUsers(String userKey, Pageable pageable) {
        return usersAggregationsRepository.findBlockedUsers(userKey, pageable);
    }

    public Flux<ExtendedUser> getBlockedByUsers(String userKey, Pageable pageable) {
        return usersAggregationsRepository.findBlockedByUsers(userKey, pageable);
    }

    public Mono<Void> setLanguage(String userKey, String language) {
        return usersAggregationsRepository.setLanguage(userKey, language);
    }

    public Mono<User> getUserBySlug(String slug) {
        return usersRepository.findAllBySlug(slug).next();
    }
}
