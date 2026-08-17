package to.orbis.v2.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
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
import to.orbis.v2.backend.models.dto.crypto.CryptoPaymentDto;
import to.orbis.v2.backend.models.entity.Group;
import to.orbis.v2.backend.models.entity.Subscription;
import to.orbis.v2.backend.models.entity.User;
import to.orbis.v2.backend.models.entity.UserSubscription;
import to.orbis.v2.backend.repositories.StripeAccountRepository;
import to.orbis.v2.backend.repositories.SubscriptionAggregationRepository;
import to.orbis.v2.backend.repositories.SubscriptionRepository;
import to.orbis.v2.backend.repositories.UserPurchaseRepository;
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
    private final UserPurchaseRepository userPurchaseRepository;
    private final StripePaymentService stripePaymentService;
    private final UsersService usersService;
    private final GroupsService groupsService;
    private final StripeService stripeService;
    private final StripeConfiguration stripeConfiguration;
    private final NotificationsService notificationsService;

    private final StatisticService statisticService;
    private final CryptoPaymentIntentClient cryptoPaymentIntentClient;
    private final PayoutWalletService payoutWalletService;
    private final NativeCurrencyService nativeCurrencyService;

    public SubscriptionsService(SubscriptionAggregationRepository subscriptionAggregationRepository, SubscriptionRepository subscriptionRepository, StripeAccountRepository stripeAccountRepository, UserSubscriptionRepository userSubscriptionRepository, UserPurchaseRepository userPurchaseRepository, StripePaymentService stripePaymentService, UsersService usersService, GroupsService groupsService, StripeService stripeService, StripeConfiguration stripeConfiguration, SubscriptionAggregationRepository subscriptionAggregationRepository1, NotificationsService notificationsService, StatisticService statisticService, CryptoPaymentIntentClient cryptoPaymentIntentClient, PayoutWalletService payoutWalletService, NativeCurrencyService nativeCurrencyService) {
        super(subscriptionAggregationRepository);
        this.subscriptionRepository = subscriptionRepository;
        this.stripeAccountRepository = stripeAccountRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.userPurchaseRepository = userPurchaseRepository;
        this.stripePaymentService = stripePaymentService;
        this.usersService = usersService;
        this.groupsService = groupsService;
        this.stripeService = stripeService;
        this.stripeConfiguration = stripeConfiguration;
        this.notificationsService = notificationsService;
        this.statisticService = statisticService;
        this.cryptoPaymentIntentClient = cryptoPaymentIntentClient;
        this.payoutWalletService = payoutWalletService;
        this.nativeCurrencyService = nativeCurrencyService;
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
                .then(subscriptionRepository.save(subscription));
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
                    return payoutWalletService.requirePayoutPubkey(userKey)
                            .flatMap(pubkey -> {
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
                .flatMap(group -> findActiveGroupSubscriptions(groupKey)
                        .count()
                        .flatMap(count -> {
                            if (!sure && count != 0) {
                                return Mono.error(() -> new SubscriptionException("There are " + count + " users, who were subscribed. Would you like to unsubscribe all users?"));
                            } else {
                                return Mono.empty();
                            }
                        })
                        .then(findActiveGroupSubscriptions(groupKey)
                                .flatMap(this::deactivateNow)
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
    public Mono<CryptoPaymentDto> subscribe(String subscriptionKey, String userKey, String payerWallet, String coin) {
        return subscriptionRepository.findOneBySubscriptionKeyAndDeletedFalse(subscriptionKey)
                .switchIfEmpty(getNoFoundError())
                .flatMap(this::validatePurchaseBeforePayment)
                .flatMap(subscription -> groupsService.findGroup(subscription.getGroupKey())
                        .flatMap(group -> {
                            if (Boolean.FALSE.equals(group.getIsSubscriptionActivate())) {
                                return Mono.error(() -> new SubscriptionException("You cannot use this subscription, because group main admin didn't activate it."));
                            }
                            return groupsService.addMember(group.getGroupKey(), userKey);
                        })
                        .then(payoutWalletService.requirePayoutPubkey(subscription.getCreatedUserKey()))
                        .flatMap(ownerWallet -> userSubscriptionRepository.findFirstBySubscriptionKeyAndUserKeyOrderByCreateTimestampDesc(subscriptionKey, userKey)
                                .flatMap(userSubscription -> createIntentForExistingUserSubscription(userSubscription, userKey, payerWallet, coin, ownerWallet, subscription))
                                .switchIfEmpty(createIntentForNewUserSubscription(userKey, payerWallet, coin, ownerWallet, subscription)))
                );
    }

    @Transactional
    public Mono<Void> unsubscribe(String subscriptionKey, String userKey) {
        return userSubscriptionRepository.findFirstBySubscriptionKeyAndUserKeyOrderByCreateTimestampDesc(subscriptionKey, userKey)
                .flatMap(userSubscription -> {
                    if (userSubscription.getStatus() == UserSubscriptionStatus.ACTIVATED) {
                        return requestDeactivation(userSubscription).thenReturn(true);
                    }
                    if (userSubscription.getStatus() == UserSubscriptionStatus.DEACTIVATED_REQUEST) {
                        return Mono.just(true);
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> userPurchaseRepository.findAllByPurchaseKeyAndUserKeyAndStatus(subscriptionKey, userKey, UserSubscriptionStatus.FINISHED)
                        .collectList()
                        .flatMap(userPurchases -> {
                            if (userPurchases.isEmpty()) {
                                return Mono.empty();
                            }
                            var now = Instant.now();
                            userPurchases.forEach(userPurchase -> {
                                userPurchase.setStatus(UserSubscriptionStatus.DEACTIVATED);
                                userPurchase.setTimestamp(now);
                            });
                            return Flux.fromIterable(userPurchases)
                                    .concatMap(userPurchaseRepository::save)
                                    .then(Mono.just(true));
                        })))
                .switchIfEmpty(Mono.error(() -> new SubscriptionException("You cannot unsubscribe, user doesn't subscribe.")))
                .then();
    }

    public Mono<CommissionDto> getSubscriptionInfo() {
        return getSubscriptionInfo(null);
    }

    public Mono<CommissionDto> getSubscriptionInfo(String userKey) {
        return getSubscriptionInfo(userKey, null);
    }

    public Mono<CommissionDto> getSubscriptionInfo(String userKey, GeoJsonPoint location) {
        Mono<NativeCurrencyService.NativeCurrencyInfo> nativeCurrencyInfo = userKey == null || userKey.isBlank()
                ? Mono.just(new NativeCurrencyService.NativeCurrencyInfo(Currency.USD, java.math.BigDecimal.ONE, false))
                : nativeCurrencyService.findForLocationOrUser(location, userKey);
        return nativeCurrencyInfo.map(info ->
                CommissionDto.builder()
                        .orbisCommission(Double.parseDouble(stripeConfiguration.getOrbisCommission()))
                        .stripeCommission(Double.parseDouble(stripeConfiguration.getStripeCommission()))
                        .stripeAdditionFee(Double.parseDouble(stripeConfiguration.getStripeAdditionFee()))
                        .currencies(List.of(Currency.values()))
                        .nativeCurrency(info.getNativeCurrency())
                        .nativeCurrencyUsdRate(info.getNativeCurrencyUsdRate())
                        .nativeCurrencyAvailable(info.isNativeCurrencyAvailable())
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

    private Mono<UserSubscription> createUserSubscription(String paymentRef, String userKey, Subscription subscription) {
        var id = new ObjectId();
        var userSubscription = UserSubscription.builder()
                .userSubscriptionKey(id.toHexString())
                .userKey(userKey)
                .subscriptionKey(subscription.getSubscriptionKey())
                .paymentRef(paymentRef)
                .groupKey(subscription.getGroupKey())
                .status(UserSubscriptionStatus.START_PAYMENT)
                .timestamp(Instant.now())
                .createTimestamp(Instant.now())
                .type(subscription.getType())
                .build();

        userSubscription.setId(id);
        return userSubscriptionRepository.save(userSubscription);
    }

    private Flux<UserSubscription> findActiveGroupSubscriptions(String groupKey) {
        return Flux.concat(
                userSubscriptionRepository.findAllByGroupKeyAndStatus(groupKey, UserSubscriptionStatus.ACTIVATED),
                userSubscriptionRepository.findAllByGroupKeyAndStatus(groupKey, UserSubscriptionStatus.DEACTIVATED_REQUEST)
        );
    }

    private Mono<UserSubscription> deactivateNow(UserSubscription userSubscription) {
        userSubscription.setStatus(UserSubscriptionStatus.DEACTIVATED);
        userSubscription.setTimestamp(Instant.now());
        return userSubscriptionRepository.save(userSubscription);
    }

    private Mono<UserSubscription> requestDeactivation(UserSubscription userSubscription) {
        var now = Instant.now();
        if (userSubscription.getEndDate() != null && userSubscription.getEndDate().isAfter(now)) {
            userSubscription.setStatus(UserSubscriptionStatus.DEACTIVATED_REQUEST);
        } else {
            userSubscription.setStatus(UserSubscriptionStatus.DEACTIVATED);
        }
        userSubscription.setTimestamp(now);
        return userSubscriptionRepository.save(userSubscription);
    }

    private Mono<CryptoPaymentDto> createIntentForNewUserSubscription(
            String userKey,
            String payerWallet,
            String coin,
            String ownerWallet,
            Subscription subscription
    ) {
        return cryptoPaymentIntentClient.createIntent(userKey, "subscription", subscription.getSubscriptionKey(), 1, payerWallet, ownerWallet, subscription.getPrice(), subscription.getCurrency(), coin)
                .flatMap(intent -> intent.getError() == null || intent.getError().isEmpty()
                        ? createUserSubscription(intent.getRef(), userKey, subscription).thenReturn(intent)
                        : Mono.just(intent));
    }

    private Mono<CryptoPaymentDto> createIntentForExistingUserSubscription(
            UserSubscription userSubscription,
            String userKey,
            String payerWallet,
            String coin,
            String ownerWallet,
            Subscription subscription
    ) {
        return cryptoPaymentIntentClient.createIntent(userKey, "subscription", subscription.getSubscriptionKey(), 1, payerWallet, ownerWallet, subscription.getPrice(), subscription.getCurrency(), coin)
                .flatMap(intent -> {
                    if (intent.getError() != null && !intent.getError().isEmpty()) {
                        return Mono.just(intent);
                    }
                    var now = Instant.now();
                    userSubscription.setTimestamp(now);
                    if (isRenewal(userSubscription)) {
                        userSubscription.setPendingRenewalPaymentRef(intent.getRef());
                    } else {
                        userSubscription.setPaymentRef(intent.getRef());
                        userSubscription.setStatus(UserSubscriptionStatus.START_PAYMENT);
                    }
                    return userSubscriptionRepository.save(userSubscription).thenReturn(intent);
                });
    }

    private boolean isRenewal(UserSubscription userSubscription) {
        return userSubscription.getEndDate() != null
                || userSubscription.getStatus() == UserSubscriptionStatus.ACTIVATED
                || userSubscription.getStatus() == UserSubscriptionStatus.DEACTIVATED_REQUEST
                || userSubscription.getStatus() == UserSubscriptionStatus.DEACTIVATED;
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
