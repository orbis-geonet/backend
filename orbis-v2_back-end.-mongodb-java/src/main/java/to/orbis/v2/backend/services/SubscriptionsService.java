package to.orbis.v2.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import to.orbis.v2.backend.configuration.StripeConfiguration;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.StripeException;
import to.orbis.v2.backend.exceptions.SubscriptionException;
import to.orbis.v2.backend.models.*;
import to.orbis.v2.backend.models.dto.SimplifiedUserDto;
import to.orbis.v2.backend.models.dto.StatisticFullSubscriptionDto;
import to.orbis.v2.backend.models.dto.SubscriptionDto;
import to.orbis.v2.backend.models.dto.UserSubscriptionDto;
import to.orbis.v2.backend.models.dto.stripe.CommissionDto;
import to.orbis.v2.backend.models.dto.stripe.StripeSecretDto;
import to.orbis.v2.backend.models.entity.Group;
import to.orbis.v2.backend.models.entity.Subscription;
import to.orbis.v2.backend.models.entity.User;
import to.orbis.v2.backend.models.entity.UserSubscription;
import to.orbis.v2.backend.repositories.StripeAccountRepository;
import to.orbis.v2.backend.repositories.SubscriptionAggregationRepository;
import to.orbis.v2.backend.repositories.SubscriptionRepository;
import to.orbis.v2.backend.repositories.UserSubscriptionRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Strings.isNullOrEmpty;

@Slf4j
@Service
public class SubscriptionsService extends UserPaymentService {
    private final SubscriptionRepository subscriptionRepository;
    private final StripeAccountRepository stripeAccountRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final StripePaymentService stripePaymentService;
    private final UsersService usersService;
    private final GroupsService groupsService;
    private final StripeService stripeService;
    private final StripeConfiguration stripeConfiguration;
    private final NotificationsService notificationsService;

    private final StatisticService statisticService;

    public SubscriptionsService(SubscriptionAggregationRepository subscriptionAggregationRepository, SubscriptionRepository subscriptionRepository, StripeAccountRepository stripeAccountRepository, UserSubscriptionRepository userSubscriptionRepository, StripePaymentService stripePaymentService, UsersService usersService, GroupsService groupsService, StripeService stripeService, StripeConfiguration stripeConfiguration, SubscriptionAggregationRepository subscriptionAggregationRepository1, NotificationsService notificationsService, StatisticService statisticService) {
        super(subscriptionAggregationRepository);
        this.subscriptionRepository = subscriptionRepository;
        this.stripeAccountRepository = stripeAccountRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.stripePaymentService = stripePaymentService;
        this.usersService = usersService;
        this.groupsService = groupsService;
        this.stripeService = stripeService;
        this.stripeConfiguration = stripeConfiguration;
        this.notificationsService = notificationsService;
        this.statisticService = statisticService;
    }

    @Transactional
    public Mono<Subscription> createSubscription(Subscription subscription) {
        if (isNullOrEmpty(subscription.getSubscriptionKey())) {
            subscription.setId(new ObjectId());
            subscription.setSubscriptionKey(subscription.getId().toHexString());
        }
        return groupsService.findGroupAndCheckMainAdmin(subscription.getGroupKey(), subscription.getCreatedUserKey(), "You should be main admin to create subscription.")
                .flatMap(group -> {
                    var subscriptionList = group.getSubscriptions();
                    if (Objects.isNull(subscriptionList)) {
                        subscriptionList = new HashSet<>();
                    }
                    subscriptionList.add(subscription.getSubscriptionKey());
                    group.setSubscriptions(subscriptionList);
                    return groupsService.saveGroup(group);
                })
                .then(
                        stripeService.createSubscriptionAsProduct(subscription)
                                .flatMap(subscription1 -> subscriptionRepository.save(subscription))
                );
    }

    @Transactional
    public Mono<Subscription> updateSubscription(Subscription subscription) {
        return groupsService.findGroupAndCheckMainAdmin(subscription.getGroupKey(), subscription.getCreatedUserKey(), "You should be main admin to edit subscription.")
                .flatMap(group -> subscriptionRepository.findOneBySubscriptionKey(subscription.getSubscriptionKey())
                        .flatMap(sub -> {
                            sub.setName(subscription.getName());
                            sub.setPrice(subscription.getPrice());
                            sub.setOriginalPrice(subscription.getOriginalPrice());
                            sub.setBenefit(subscription.getBenefit());
                            sub.setCurrency(subscription.getCurrency());
                            sub.setTimestamp(subscription.getTimestamp());
                            sub.setImagesName(subscription.getImagesName());
                            return Mono.just(sub);
                        })
                        .flatMap(sub -> {
                            if (Objects.nonNull(subscription.getPrice()) || Objects.nonNull(subscription.getCurrency())) {
                                return stripeService.updatePrice(sub);
                            } else {
                                return Mono.just(sub);
                            }
                        })
                        .flatMap(subscriptionRepository::save)
                );
    }

    @Transactional
    public Mono<Void> deleteSubscription(String groupKey, String subscriptionKey, String userKey) {
        return groupsService.findGroupAndCheckMainAdmin(groupKey, userKey, "You should be main admin to delete subscription.")
                .flatMap(group -> subscriptionRepository.findOneBySubscriptionKey(subscriptionKey)
                        .switchIfEmpty(getNoFoundError())
                        .flatMap(subscription -> {
                                    subscription.setDeleted(true);
                                    subscription.setTimestamp(Instant.now());
                                    return subscriptionRepository.save(subscription);
                                }
                            )
                        .flatMap(subscription -> {
                            group.getSubscriptions().remove(subscription.getSubscriptionKey());
                            groupsService.saveGroup(group);
                            return Mono.just(subscription);
                        })
                        .flatMap(stripeService::archivePrice)
                )
                .then();
    }

    @Transactional
    public Mono<Void> subscriptionGroupActivate(String groupKey, String userKey) {
        return groupsService.findGroupAndCheckMainAdmin(groupKey, userKey, "You should be main admin to activate subscription.")
                .flatMap(group -> {
                    if (Objects.isNull(group.getSubscriptions()) || group.getSubscriptions().isEmpty()) {
                        return Mono.error(() -> new NoDataFoundException("There are no subscription for group."));
                    }
                    if (Objects.isNull(group.getMainAdmin()) || group.getMainAdmin().isEmpty()) {
                        return Mono.error(() -> new NoDataFoundException("There is no main user for group."));
                    }
                    return stripeAccountRepository.findByUserKeyAndDeletedFalseAndStatusIn(userKey, List.of(StripeAccountStatus.READY_TO_USE))
                            .switchIfEmpty(Mono.error(() -> new NoDataFoundException("There is no validated stripe account for main user. userKey: " + userKey)))
                            .flatMap(account -> {
                                group.setIsSubscriptionActivate(true);
                                group.setTimestamp(Instant.now());
                                return groupsService.saveGroup(group)
                                        .then();
                            })
                            .then();
                });
    }

    @Transactional
    public Mono<Void> subscriptionGroupDeactivate(String groupKey, String userKey, boolean sure) {
        return groupsService.findGroupAndCheckMainAdmin(groupKey, userKey, "You should be main admin to deactivate subscription.")
                .flatMap(group -> {
                    if (Boolean.FALSE.equals(group.getIsSubscriptionActivate())) {
                        return Mono.error(() -> new NoDataFoundException("Subscription doesn't activated."));
                    } else {
                        return Mono.just(group);
                    }
                })
                .flatMap(group -> userSubscriptionRepository.findAllByGroupKeyAndStatus(groupKey, UserSubscriptionStatus.ACTIVATED)
                        .count()
                        .flatMap(count -> {
                            if (!sure && count != 0) {
                                return Mono.error(() -> new SubscriptionException("There are " + count + " users, who were subscribed. Would you like to unsubscribe all users?"));
                            } else {
                                return Mono.empty();
                            }
                        })
                        .then(userSubscriptionRepository.findAllByGroupKeyAndStatus(groupKey, UserSubscriptionStatus.ACTIVATED)
                                .flatMap(userSubscription -> unsubscribe(userSubscription.getSubscriptionKey(), userSubscription.getUserKey()))
                                .then(Mono.just(group))))
                .flatMap(group -> {
                    group.setTimestamp(Instant.now());
                    group.setIsSubscriptionActivate(false);
                    return groupsService.saveGroup(group)
                            .then();
                });
    }

    public Mono<Subscription> getSubscription(String subscriptionKey) {
        return subscriptionRepository.findOneBySubscriptionKeyAndDeletedFalse(subscriptionKey)
                .switchIfEmpty(getNoFoundError());
    }

    public Flux<SubscriptionDto> getAllGroupSubscription(String groupKey, String userKey, PageRequest pageable) {
        return subscriptionAggregationRepository.findAll(groupKey, userKey, pageable);
    }

    @Transactional
    public Mono<StripeSecretDto> subscribe(String subscriptionKey, String userKey) {
        return subscriptionRepository.findOneBySubscriptionKeyAndDeletedFalse(subscriptionKey)
                .switchIfEmpty(getNoFoundError())
                .flatMap(this::validatePurchaseBeforePayment)
                .flatMap(subscription -> checkPurchaseCurrency(subscription, userKey))
                .flatMap(subscription -> groupsService.findGroup(subscription.getGroupKey())
                        .flatMap(group -> {
                            if (Boolean.FALSE.equals(group.getIsSubscriptionActivate())) {
                                return Mono.error(() -> new SubscriptionException("You cannot use this subscription, because group main admin didn't activate it."));
                            }
                            return groupsService.addMember(group.getGroupKey(), userKey);
                        })
                        .then(userSubscriptionRepository.findBySubscriptionKeyAndUserKeyAndStatus(subscriptionKey, userKey, UserSubscriptionStatus.ACTIVATED)
                                .flatMap(userSubscription -> Mono.error(() -> new SubscriptionException("You cannot subscribe, user has already activated this subscription.")))
                                .then(Mono.just(subscription))
                        )
                        .then(usersService.findUser(userKey, "User not found."))
                        .flatMap(user -> {
                            if (Objects.nonNull(user.getCustomerStripeId()) && !user.getCustomerStripeId().isEmpty()) {
                                return Mono.just(user);
                            } else {
                                return stripeService.createCustomer(user.getUserKey())
                                        .flatMap(customerUserStripeId -> {
                                            user.setCustomerStripeId(customerUserStripeId);
                                            return usersService.save(user);
                                        });
                            }
                        })
                        .flatMap(user -> usersService.findUser(subscription.getCreatedUserKey(), "User cannot be found")
                                .flatMap(groupOwner -> stripePaymentService.subscribe(subscription, user, groupOwner))
                                .flatMap(subscriptionResult -> createUserSubscription(subscriptionResult.getStripeId(), user.getUserKey(), subscription)
                                .flatMap(userSubscription -> stripePaymentService.createFirstPaymentAndReturnCustomerSecret(subscriptionResult, userSubscription.getUserSubscriptionKey(), user.getCustomerStripeId(), subscription)
                                ))
                        )
                );
    }

    @Transactional
    public Mono<Void> unsubscribe(String subscriptionKey, String userKey) {
        return userSubscriptionRepository.findBySubscriptionKeyAndUserKeyAndStatus(subscriptionKey, userKey, UserSubscriptionStatus.ACTIVATED)
                .switchIfEmpty(Mono.error(() -> new SubscriptionException("You cannot unsubscribe, user doesn't subscribe.")))
                .flatMap(userSubscription -> {
                    userSubscription.setStatus(UserSubscriptionStatus.DEACTIVATED_REQUEST);
                    userSubscription.setTimestamp(Instant.now());
                    return stripePaymentService.unsubscribe(userSubscription)
                            .then(userSubscriptionRepository.save(userSubscription))
                            .then();
                });
    }

    public Mono<CommissionDto> getSubscriptionInfo() {
        return Mono.just(
                CommissionDto.builder()
                        .orbisCommission(Double.parseDouble(stripeConfiguration.getOrbisCommission()))
                        .stripeCommission(Double.parseDouble(stripeConfiguration.getStripeCommission()))
                        .stripeAdditionFee(Double.parseDouble(stripeConfiguration.getStripeAdditionFee()))
                        .currencies(List.of(Currency.values()))
                        .build()
        );
    }

    public Flux<UserSubscriptionDto> getMySubscriptions(String groupKey, String userKey) {
        return subscriptionAggregationRepository.findAllUserSubscriptionByUserKey(groupKey, userKey);
    }

    public Flux<SimplifiedUserDto> getSubscribers(String groupKey, String subscriptionKey, String userKey, PageRequest page) {
        return groupsService.findGroupAndCheckMainAdmin(groupKey, userKey, "Only main Admin can see statistic information")
                .thenMany(subscriptionAggregationRepository.findGroupUserStatistic(groupKey, subscriptionKey, page));
    }

    public Mono<StatisticFullSubscriptionDto> getStatistic(String groupKey, String subscriptionKey, String userKey, StatisticSubscriptionType type) {
        return groupsService.findGroupAndCheckMainAdmin(groupKey, userKey, "Only main Admin can see statistic information")
                .then(statisticService.countStatistic(
                        subscriptionKey,
                        type,
                        subscriptionAggregationRepository::getCountForStatistic,
                        subscriptionAggregationRepository::getAmountForStatistic
                ))
                .flatMap(statisticResult -> Mono.just(
                                StatisticFullSubscriptionDto.builder()
                                        .resultList(statisticResult)
                                        .totalAmount(statisticResult.stream().reduce(0.0, (result, element) -> result + element.getAmount(), Double::sum))
                                        .totalNumber(statisticResult.stream().reduce(0L, (result, element) -> result + element.getNumber(), Long::sum))
                                        .build()
                        )
                );
    }

    public Mono<Void> submitDeleteSubscriptionEventFromStripeWebhook(com.stripe.model.Subscription subscription) {
        return userSubscriptionRepository.findBySubscriptionStripeId(subscription.getId())
                .flatMap(userSubscription -> {
                    userSubscription.setStatus(UserSubscriptionStatus.DEACTIVATED);
                    userSubscription.setTimestamp(Instant.now());
                    userSubscription.setEndDate(Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()));
                    return userSubscriptionRepository.save(userSubscription);
                })
                .flatMap(userSubscription -> sendAdminNotification(StripeSubscriptionEventType.DELETED, userSubscription, NotificationType.SUBSCRIPTION_MAIN_USER)
                        .then(sendAdminNotification(StripeSubscriptionEventType.DELETED, userSubscription, NotificationType.SUBSCRIPTION_END_USER)))
                .then();

    }

    public Mono<Void> submitUpdateSubscriptionFromStripeWebhook(com.stripe.model.Subscription subscription) {
        return userSubscriptionRepository.findBySubscriptionStripeId(subscription.getId())
                .flatMap(userSubscription -> {
                    if (userSubscription.getStatus() == UserSubscriptionStatus.ACTIVATED && subscription.getStatus().equals("past_due")) {
                        return sendAdminNotification(StripeSubscriptionEventType.PAYMENT_PROBLEM, userSubscription, NotificationType.SUBSCRIPTION_END_USER);
                    } else if (userSubscription.getStatus() == UserSubscriptionStatus.ACTIVATED) {
                        return stripePaymentService.createNewUpdateSubscriptionPayment(userSubscription, subscription)
                                .then(sendAdminNotification(StripeSubscriptionEventType.UPDATED, userSubscription, NotificationType.SUBSCRIPTION_MAIN_USER));
                    } else if (subscription.getStatus().equals("active")){
                        return userSubscriptionRepository.findBySubscriptionKeyAndUserKeyAndStatus(userSubscription.getSubscriptionKey(), userSubscription.getUserKey(), UserSubscriptionStatus.ACTIVATED)
                                .flatMap(userSubscriptionOld -> Mono.error(() -> new SubscriptionException("You cannot subscribe, user has already activated this subscription.")))
                                .then(Mono.defer(() -> {
                                    userSubscription.setStatus(UserSubscriptionStatus.ACTIVATED);
                                    userSubscription.setTimestamp(Instant.now());
                                    userSubscription.setStartDate(Instant.now());
                                    userSubscription.setLastPaymentTime(Instant.now());
                                    return userSubscriptionRepository.save(userSubscription)
                                            .then(sendAdminNotification(StripeSubscriptionEventType.CREATED, userSubscription, NotificationType.SUBSCRIPTION_MAIN_USER))
                                            .then(sendAdminNotification(StripeSubscriptionEventType.CREATED, userSubscription, NotificationType.SUBSCRIPTION_END_USER));
                                }));
                    } else if (subscription.getStatus().equals("incomplete_expired")){
                        userSubscription.setStatus(UserSubscriptionStatus.CANCELED_BY_STRIPE_INCOMPLETE_PAYMENT);
                        userSubscription.setTimestamp(Instant.now());
                        return userSubscriptionRepository.save(userSubscription)
                                .then();
                    } else {
                        return Mono.error(() -> new StripeException(String.format(
                                        "Something wrong with subscription update. userSubscriptionId=%s stripeSubscriptionId=%s stripeSubscriptionStatus=%s",
                                        userSubscription.getId(),
                                        subscription.getId(),
                                        subscription.getStatus())
                        ));
                    }
                });
    }

    public Mono<Void> sendAdminNotification(StripeSubscriptionEventType type, UserSubscription userSubscription, NotificationType notificationType) {
        if (type == StripeSubscriptionEventType.UPDATED) {
            return Mono.empty();
        } else {
            return subscriptionRepository.findOneBySubscriptionKeyAndDeletedFalse(userSubscription.getSubscriptionKey())
                    .flatMap(subscription -> groupsService.findGroup(subscription.getGroupKey())
                            .flatMap(group -> usersService.findUser(userSubscription.getUserKey(), "Cannot find user")
                                    .flatMap(user -> {
                                        if (notificationType == NotificationType.SUBSCRIPTION_MAIN_USER) {
                                            return usersService.findUser(group.getMainAdmin(), "Cannot find main admin")
                                                    .flatMap(mainAdmin -> notify(type, subscription, group, user, mainAdmin, notificationType));
                                        } else if (notificationType == NotificationType.SUBSCRIPTION_END_USER) {
                                            return notify(type, subscription, group, user, user, notificationType);
                                        } else {
                                            return Mono.empty();
                                        }
                                    })));
        }
    }

    private Mono<UserSubscription> createUserSubscription(String stripeSubscriptionId, String userKey, Subscription subscription) {
        var id = new ObjectId();
        var userSubscription = UserSubscription.builder()
                .userSubscriptionKey(id.toHexString())
                .userKey(userKey)
                .subscriptionKey(subscription.getSubscriptionKey())
                .subscriptionStripeId(stripeSubscriptionId)
                .groupKey(subscription.getGroupKey())
                .status(UserSubscriptionStatus.START_PAYMENT)
                .timestamp(Instant.now())
                .createTimestamp(Instant.now())
                .type(subscription.getType())
                .build();

        userSubscription.setId(id);
        return userSubscriptionRepository.save(userSubscription);
    }

    private Mono<Void> notify(
            StripeSubscriptionEventType type,
            Subscription subscription,
            Group group,
            User user,
            User receiver,
            NotificationType notificationType
    ) {
        notificationsService.notifySubscription(type, subscription, group, user, receiver, notificationType)
                .subscribeOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.boundedElastic())
                .subscribe(_ignored -> {
                }, error -> log.error("Failed to notify main admin regarding subscription event {} {}", type, error));
        return Mono.empty();
    }
}
