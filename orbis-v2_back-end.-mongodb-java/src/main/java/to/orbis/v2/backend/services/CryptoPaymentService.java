package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.crypto.CryptoPaymentStatusDto;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
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
    private final CryptoPaymentIntentClient cryptoPaymentIntentClient;

    public Mono<Void> confirmPayment(String ref, String txSignature) {
        if (ref == null || ref.isEmpty()) {
            return Mono.empty();
        }
        if (txSignature == null || txSignature.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature is required."));
        }
        return cryptoPaymentIntentClient.getStatus(ref)
                .onErrorMap(error -> !(error instanceof ResponseStatusException), error -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot verify payment status.", error))
                .flatMap(status -> {
                    if (status == null || !"PAID".equals(status.getStatus())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment is not confirmed on chain."));
                    }
                    String confirmedSignature = status.getTxSignature() == null || status.getTxSignature().isEmpty()
                            ? status.getDistributionTxSignature()
                            : status.getTxSignature();
                    if (confirmedSignature == null || confirmedSignature.isEmpty() || !Objects.equals(confirmedSignature, txSignature)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature does not match confirmed transaction."));
                    }
                    return findSubscriptionByAnyPaymentRef(ref)
                            .flatMap(userSubscription -> activateSubscription(userSubscription, txSignature, ref, status).then())
                            .switchIfEmpty(Mono.defer(() -> userPurchaseRepository.findByPaymentRef(ref)
                                    .flatMap(userPurchase -> activatePurchase(userPurchase, txSignature, status).then())))
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No local payment record found for this payment reference.")));
                })
                .then();
    }

    public Mono<CryptoPaymentStatusDto> resolvePaymentStatus(CryptoPaymentStatusDto status, String userKey) {
        if (status == null || status.getRef() == null || status.getRef().isEmpty()) {
            return Mono.justOrEmpty(status);
        }
        return findSubscriptionByAnyPaymentRef(status.getRef())
                .map(userSubscription -> {
                    if (!Objects.equals(userSubscription.getUserKey(), userKey)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                    }
                    if ("PAID".equals(status.getStatus()) && (userSubscription.getStatus() != UserSubscriptionStatus.ACTIVATED || Objects.equals(userSubscription.getPendingRenewalPaymentRef(), status.getRef()))) {
                        status.setStatus("CONFIRMING");
                        status.setMessage("Finalizing payment.");
                    }
                    return status;
                })
                .switchIfEmpty(Mono.defer(() -> userPurchaseRepository.findByPaymentRef(status.getRef())
                        .map(userPurchase -> {
                            if (!Objects.equals(userPurchase.getUserKey(), userKey)) {
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                            }
                            if ("PAID".equals(status.getStatus()) && userPurchase.getStatus() != UserSubscriptionStatus.FINISHED) {
                                status.setStatus("CONFIRMING");
                                status.setMessage("Finalizing payment.");
                            }
                            return status;
                        })))
                .switchIfEmpty(Mono.defer(() -> {
                    if ("PAID".equals(status.getStatus())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Payment is confirmed, but the local purchase record was not found. Please contact support."));
                    }
                    return Mono.just(status);
                }));
    }

    private Mono<UserSubscription> activateSubscription(UserSubscription userSubscription, String txSignature, String ref, CryptoPaymentStatusDto paymentStatus) {
        boolean renewal = Objects.equals(userSubscription.getPendingRenewalPaymentRef(), ref);
        if (userSubscription.getStatus() == UserSubscriptionStatus.ACTIVATED && !renewal) {
            return Mono.just(userSubscription);
        }
        return subscriptionRepository.findOneBySubscriptionKeyAndDeletedFalse(userSubscription.getSubscriptionKey())
                .flatMap(subscription -> {
                    Instant now = Instant.now();
                    Instant base = renewal && userSubscription.getEndDate() != null && userSubscription.getEndDate().isAfter(now)
                            ? userSubscription.getEndDate()
                            : now;
                    userSubscription.setStatus(UserSubscriptionStatus.ACTIVATED);
                    if (!renewal || userSubscription.getStartDate() == null) {
                        userSubscription.setStartDate(now);
                    }
                    userSubscription.setEndDate(computeEndDate(subscription, base));
                    userSubscription.setLastPaymentTime(now);
                    userSubscription.setTimestamp(now);
                    userSubscription.setPendingRenewalPaymentRef(null);
                    if (renewal) {
                        userSubscription.setLastRenewalPaymentRef(ref);
                        userSubscription.setLastReminderDate(null);
                    } else {
                        userSubscription.setCodes(List.of(CodeUtils.createRandomCode()));
                    }
                    return userSubscriptionRepository.save(userSubscription)
                            .flatMap(saved -> savePayment(subscription, saved.getUserSubscriptionKey(), subscription.getPrice(), ref, txSignature, PaymentType.SUBSCRIPTION_PAYMENT, paymentStatus)
                                    .flatMap(payment -> emailSendingService.sendEmails(saved, payment).onErrorResume(error -> Mono.empty()).thenReturn(saved)));
                });
    }

    private Mono<UserSubscription> findSubscriptionByAnyPaymentRef(String ref) {
        return userSubscriptionRepository.findByPaymentRef(ref)
                .switchIfEmpty(userSubscriptionRepository.findByPendingRenewalPaymentRef(ref))
                .switchIfEmpty(userSubscriptionRepository.findByLastRenewalPaymentRef(ref));
    }

    private Mono<UserPurchase> activatePurchase(UserPurchase userPurchase, String txSignature, CryptoPaymentStatusDto paymentStatus) {
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
                    var amount = subscription.getPrice().multiply(BigDecimal.valueOf(number));
                    return userPurchaseRepository.save(userPurchase)
                            .flatMap(saved -> savePayment(subscription, saved.getUserPurchaseKey(), amount, userPurchase.getPaymentRef(), txSignature, PaymentType.ONE_TIME_PAYMENT, paymentStatus)
                                    .flatMap(payment -> emailSendingService.sendEmails(saved, payment).onErrorResume(error -> Mono.empty()).thenReturn(saved)));
                });
    }

    private Mono<Payment> savePayment(Subscription subscription, String key, BigDecimal amount, String ref, String txSignature, PaymentType type, CryptoPaymentStatusDto paymentStatus) {
        var id = new ObjectId();
        var payment = Payment.builder()
                .paymentId(id.toHexString())
                .userSubscriptionKey(key)
                .amount(amount)
                .amountAfterStripCommission(amount)
                .currency(subscription.getCurrency())
                .paymentType(type)
                .status(PaymentStatus.SUCCEEDED)
                .paymentRef(ref)
                .txSignature(txSignature)
                .solscanUrl(paymentStatus == null ? null : paymentStatus.getSolscanUrl())
                .solanaCluster(paymentStatus == null ? null : paymentStatus.getSolanaCluster())
                .ownerCutOrbis(paymentStatus == null ? null : paymentStatus.getOwnerCutOrbis())
                .cloneCutOrbis(paymentStatus == null ? null : paymentStatus.getCloneCutOrbis())
                .treasuryCutOrbis(paymentStatus == null ? null : paymentStatus.getTreasuryCutOrbis())
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
