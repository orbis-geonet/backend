package to.orbis.v2.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.PartnerException;
import to.orbis.v2.backend.exceptions.SubscriptionException;
import to.orbis.v2.backend.models.StatisticSubscriptionType;
import to.orbis.v2.backend.models.UserSubscriptionStatus;
import to.orbis.v2.backend.models.dto.StatisticFullSubscriptionDto;
import to.orbis.v2.backend.models.dto.UserPurchaseDto;
import to.orbis.v2.backend.models.dto.email.EmailType;
import to.orbis.v2.backend.models.dto.crypto.CryptoPaymentDto;
import to.orbis.v2.backend.models.entity.Subscription;
import to.orbis.v2.backend.models.entity.UserPurchase;
import to.orbis.v2.backend.repositories.*;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@Service
public class PurchaseService extends UserPaymentService{
    private final SubscriptionRepository subscriptionRepository;
    private final UserPurchaseAggregationRepository userPurchaseAggregationRepository;
    private final UserPurchaseRepository userPurchaseRepository;
    private final StripePaymentService stripePaymentService;
    private final UsersService usersService;
    private final GroupsService groupsService;
    private final StripeService stripeService;
    private final EmailSendingService emailSendingService;
    private final StatisticService statisticService;
    private final CryptoPaymentIntentClient cryptoPaymentIntentClient;
    private final PayoutWalletService payoutWalletService;

    public PurchaseService(SubscriptionAggregationRepository subscriptionAggregationRepository, SubscriptionRepository subscriptionRepository, UserPurchaseAggregationRepository userPurchaseAggregationRepository, UserPurchaseRepository userPurchaseRepository, StripePaymentService stripePaymentService, UsersService usersService, GroupsService groupsService, StripeService stripeService, EmailSendingService emailSendingService, StatisticService statisticService, CryptoPaymentIntentClient cryptoPaymentIntentClient, PayoutWalletService payoutWalletService) {
        super(subscriptionAggregationRepository);
        this.subscriptionRepository = subscriptionRepository;
        this.userPurchaseAggregationRepository = userPurchaseAggregationRepository;
        this.userPurchaseRepository = userPurchaseRepository;
        this.stripePaymentService = stripePaymentService;
        this.usersService = usersService;
        this.groupsService = groupsService;
        this.stripeService = stripeService;
        this.emailSendingService = emailSendingService;
        this.statisticService = statisticService;
        this.cryptoPaymentIntentClient = cryptoPaymentIntentClient;
        this.payoutWalletService = payoutWalletService;
    }

    @Transactional
    public Mono<CryptoPaymentDto> buyPurchase(String subscriptionKey, Integer number, String userKey, String payerWallet) {
        return subscriptionRepository.findOneBySubscriptionKeyAndDeletedFalse(subscriptionKey)
                .switchIfEmpty(getNoFoundError())
                .flatMap(this::validatePurchaseBeforePayment)
                .flatMap(purchase -> checkPurchaseCurrency(purchase, userKey))
                .flatMap(subscription -> groupsService.findGroup(subscription.getGroupKey())
                        .flatMap(group -> {
                            if (Boolean.FALSE.equals(group.getIsSubscriptionActivate())) {
                                return Mono.error(() -> new SubscriptionException("You cannot use this purchase, because group main admin didn't activate it."));
                            }
                            return groupsService.addMember(group.getGroupKey(), userKey);
                        })
                        .then(payoutWalletService.requirePayoutPubkey(subscription.getCreatedUserKey()))
                        .flatMap(ownerWallet -> {
                            int qty = number == null ? 1 : number;
                            double priceUsd = subscription.getPrice().doubleValue() * qty;
                            return cryptoPaymentIntentClient.createIntent(userKey, "purchase", subscriptionKey, qty, payerWallet, ownerWallet, priceUsd)
                                    .flatMap(intent -> createUserPurchase(intent.getRef(), userKey, subscription, qty).thenReturn(intent));
                        })
                );
    }

    private Mono<UserPurchase> createUserPurchase(String paymentRef, String userKey, Subscription subscription, Integer number) {
        var id = new ObjectId();
        var purchaseSubscription = UserPurchase.builder()
                .userPurchaseKey(id.toHexString())
                .userKey(userKey)
                .purchaseKey(subscription.getSubscriptionKey())
                .paymentRef(paymentRef)
                .number(number)
                .groupKey(subscription.getGroupKey())
                .status(UserSubscriptionStatus.START_PAYMENT)
                .timestamp(Instant.now())
                .createTimestamp(Instant.now())
                .build();

        purchaseSubscription.setId(id);
        return userPurchaseRepository.save(purchaseSubscription);
    }

    public Flux<UserPurchaseDto> getMyPurchases(String groupKey, String userKey, PageRequest page) {
        return userPurchaseAggregationRepository.findAllUserPurchaseByUserKey(groupKey, userKey, page);
    }

    public Flux<UserPurchaseDto> getAllGroupPurchase(String groupKey, String purchaseKey, String userKey, PageRequest page) {
        return groupsService.findGroupAndCheckMainAdmin(groupKey, userKey, "Only main Admin can see statistic information")
                .thenMany(userPurchaseAggregationRepository.getAllGroupPurchase(groupKey, purchaseKey, page));
    }

    public Mono<Void> resendPurchaseEmail(String userPurchaseKey, String userKey) {
        return userPurchaseRepository.findByUserPurchaseKey(userPurchaseKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("There is no userPurchase with key " + userPurchaseKey)))
                .flatMap(userPurchase -> groupsService.findGroup(userPurchase.getGroupKey())
                        .flatMap(group -> {
//                            if (userPurchase.getUserKey().equals(userKey)) {
                                return emailSendingService.sendPurchaseEmail(group, userPurchase, EmailType.ADMIN);
//                            } else {
//                                return Mono.error(() -> new PartnerException("You cannot send email to this user"));
//                            }
                        })
                );
    }

    public Mono<StatisticFullSubscriptionDto> getStatistic(String groupKey, String subscriptionKey, String userKey, StatisticSubscriptionType type) {
        return groupsService.findGroupAndCheckMainAdmin(groupKey, userKey, "Only main Admin can see statistic information")
                .then(statisticService.countStatistic(
                        subscriptionKey,
                        type,
                        userPurchaseAggregationRepository::getCountForStatistic,
                        userPurchaseAggregationRepository::getAmountForStatistic
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
}
