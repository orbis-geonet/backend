package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.PaymentStatus;
import to.orbis.v2.backend.models.PaymentType;
import to.orbis.v2.backend.models.SubscriptionInterval;
import to.orbis.v2.backend.models.UserSubscriptionStatus;
import to.orbis.v2.backend.models.entity.Payment;
import to.orbis.v2.backend.models.entity.Subscription;
import to.orbis.v2.backend.models.entity.UserPurchase;
import to.orbis.v2.backend.models.entity.UserSubscription;
import to.orbis.v2.backend.repositories.PaymentRepository;
import to.orbis.v2.backend.repositories.SubscriptionRepository;
import to.orbis.v2.backend.repositories.UserPurchaseRepository;
import to.orbis.v2.backend.repositories.UserSubscriptionRepository;
import to.orbis.v2.backend.utils.CodeUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoPaymentService {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserPurchaseRepository userPurchaseRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final EmailSendingService emailSendingService;

    public Mono<Void> confirmPayment(String ref, String txSignature) {
        if (ref == null || ref.isEmpty()) {
            return Mono.empty();
        }
        return userSubscriptionRepository.findByPaymentRef(ref)
                .flatMap(userSubscription -> activateSubscription(userSubscription, txSignature).then())
                .switchIfEmpty(Mono.defer(() -> userPurchaseRepository.findByPaymentRef(ref)
                        .flatMap(userPurchase -> activatePurchase(userPurchase, txSignature).then())))
                .then();
    }

    private Mono<UserSubscription> activateSubscription(UserSubscription userSubscription, String txSignature) {
        if (userSubscription.getStatus() == UserSubscriptionStatus.ACTIVATED) {
            return Mono.just(userSubscription);
        }
        return subscriptionRepository.findOneBySubscriptionKeyAndDeletedFalse(userSubscription.getSubscriptionKey())
                .flatMap(subscription -> {
                    Instant now = Instant.now();
                    userSubscription.setStatus(UserSubscriptionStatus.ACTIVATED);
                    userSubscription.setStartDate(now);
                    userSubscription.setEndDate(computeEndDate(subscription, now));
                    userSubscription.setLastPaymentTime(now);
                    userSubscription.setTimestamp(now);
                    userSubscription.setCodes(List.of(CodeUtils.createRandomCode()));
                    return userSubscriptionRepository.save(userSubscription)
                            .flatMap(saved -> savePayment(subscription, saved.getUserSubscriptionKey(), userSubscription.getPaymentRef(), txSignature, PaymentType.SUBSCRIPTION_PAYMENT).thenReturn(saved))
                            .flatMap(saved -> emailSendingService.sendEmails(saved).thenReturn(saved).onErrorReturn(saved));
                });
    }

    private Mono<UserPurchase> activatePurchase(UserPurchase userPurchase, String txSignature) {
        if (userPurchase.getStatus() == UserSubscriptionStatus.FINISHED) {
            return Mono.just(userPurchase);
        }
        return subscriptionRepository.findOneBySubscriptionKeyAndDeletedFalse(userPurchase.getPurchaseKey())
                .flatMap(subscription -> {
                    Instant now = Instant.now();
                    int number = userPurchase.getNumber() == null ? 1 : userPurchase.getNumber();
                    userPurchase.setStatus(UserSubscriptionStatus.FINISHED);
                    userPurchase.setLastPaymentTime(now);
                    userPurchase.setTimestamp(now);
                    userPurchase.setCodes(createCodes(number));
                    return userPurchaseRepository.save(userPurchase)
                            .flatMap(saved -> savePayment(subscription, saved.getUserPurchaseKey(), userPurchase.getPaymentRef(), txSignature, PaymentType.ONE_TIME_PAYMENT).thenReturn(saved))
                            .flatMap(saved -> emailSendingService.sendEmails(saved).thenReturn(saved).onErrorReturn(saved));
                });
    }

    private Mono<Payment> savePayment(Subscription subscription, String key, String ref, String txSignature, PaymentType type) {
        var id = new ObjectId();
        var payment = Payment.builder()
                .paymentId(id.toHexString())
                .userSubscriptionKey(key)
                .amount(subscription.getPrice())
                .currency(subscription.getCurrency())
                .paymentType(type)
                .status(PaymentStatus.SUCCEEDED)
                .paymentRef(ref)
                .txSignature(txSignature)
                .timestamp(Instant.now())
                .createTimestamp(Instant.now())
                .build();
        payment.setId(id);
        return paymentRepository.save(payment);
    }

    private List<String> createCodes(int number) {
        return IntStream.range(0, number)
                .mapToObj(it -> CodeUtils.createRandomCode())
                .collect(Collectors.toList());
    }

    private Instant computeEndDate(Subscription subscription, Instant now) {
        int period = subscription.getPeriod() == null || subscription.getPeriod() < 1 ? 1 : subscription.getPeriod();
        ZonedDateTime start = now.atZone(ZoneOffset.UTC);
        if (subscription.getInterval() == SubscriptionInterval.YEAR) {
            return start.plusYears(period).toInstant();
        }
        return start.plusMonths(period).toInstant();
    }
}
