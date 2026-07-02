package to.orbis.v2.backend.services;

import com.stripe.model.Charge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.StripeConfiguration;
import to.orbis.v2.backend.exceptions.StripeException;
import to.orbis.v2.backend.models.*;
import to.orbis.v2.backend.models.dto.stripe.StripeSecretDto;
import to.orbis.v2.backend.models.dto.stripe.FirstPaymentResult;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.repositories.PaymentRepository;
import to.orbis.v2.backend.repositories.StripeTransferRepository;
import to.orbis.v2.backend.repositories.UserPurchaseRepository;
import to.orbis.v2.backend.repositories.UserSubscriptionRepository;
import to.orbis.v2.backend.utils.CodeUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static to.orbis.v2.backend.models.StripeTransferType.GROUP_ADMIN;
import static to.orbis.v2.backend.models.StripeTransferType.PARTNER;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentService {
    private final StripeService stripeService;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserPurchaseRepository userPurchaseRepository;
    private final PaymentRepository paymentRepository;
    private final StripeConfiguration stripeConfiguration;
    private final GroupsService groupsService;
    private final PartnerService partnerService;
    private final StripeTransferRepository stripeTransferRepository;
    private final EmailSendingService emailSendingService;

    public Mono<FirstPaymentResult> subscribe(Subscription subscription, User user, User groupOwner) {
        return stripeService.subscribe(subscription, user, groupOwner);
    }

    public Mono<FirstPaymentResult> buyPurchase(Subscription subscription, User user, Integer number) {
        return stripeService.makeOneTimePayment(subscription, user, number);
    }

    public Mono<Void> unsubscribe(UserSubscription userSubscription) {
        return stripeService.unsubscribe(userSubscription)
                .then();
    }

    // if there is an entity by invoice - update subscription
    // if no entity - save this charge, because it's the first subscription payment ???????
    public Mono<Void> updatePaymentFromStripe(Charge charge) {
        return workWithSubscriptionPaymentFromStripeByInvoice(charge)
                .then();
    }

    private Mono<Payment> workWithSubscriptionPaymentFromStripeByInvoice(Charge charge) {
        log.info("createSubscriptionPaymentFromStripe: chargeId={}", charge.getId());
        return paymentRepository.findByPaymentIntentStripeId(charge.getPaymentIntent())
                .switchIfEmpty(Mono.error(() -> new StripeException("Cannot find payment by invoice")))
                .flatMap(payment -> {
                    if (payment.getPaymentType().equals(PaymentType.ONE_TIME_PAYMENT)) {
                        return handleOneTimePayment(payment, charge);
                    } else {
                        return handleSubscriptionPayment(payment, charge);
                    }
                });
    }

    private Mono<Payment> handleOneTimePayment(Payment payment, Charge charge) {
        return userPurchaseRepository.findByUserPurchaseKey(payment.getUserSubscriptionKey())
                .flatMap(userPurchase -> {
                    long netAmount = (long) (charge.getAmount() - (charge.getAmount() * Double.parseDouble(stripeConfiguration.getStripeCommission())) - Double.parseDouble(stripeConfiguration.getStripeAdditionFee()) * 100);

                    updatePayment(netAmount, charge, payment);

                    userPurchase.setStatus(UserSubscriptionStatus.FINISHED);
                    userPurchase.setTimestamp(Instant.now());
                    userPurchase.setLastPaymentTime(Instant.now());

                    userPurchase.setCodes(createCodes(userPurchase.getNumber()));
                    return userPurchaseRepository.save(userPurchase)
                            .then(sendTransfer(charge, payment, userPurchase.getGroupKey(), netAmount))
                            .flatMap(orderId -> {
                                payment.setStripeOrderId(orderId);
                                return paymentRepository.save(payment);
                            })
                            .flatMap(newPayment -> emailSendingService.sendEmails(userPurchase)
                                    .then(Mono.just(newPayment))
                            );
                });
    }

    private List<String> createCodes(Integer number) {
        return IntStream.range(0, number)
                .mapToObj(it -> CodeUtils.createRandomCode())
                .collect(toList());
    }

    private Mono<Payment> handleSubscriptionPayment(Payment payment, Charge charge) {
        return userSubscriptionRepository.findByUserSubscriptionKey(payment.getUserSubscriptionKey())
                .flatMap(userSubscription -> {
                    long netAmount = (long) (charge.getAmount() - (charge.getAmount() * Double.parseDouble(stripeConfiguration.getStripeCommission())) - Double.parseDouble(stripeConfiguration.getStripeAdditionFee()) * 100);

                    updatePayment(netAmount, charge, payment);

                    userSubscription.setStatus(UserSubscriptionStatus.ACTIVATED);
                    if (Objects.isNull(userSubscription.getStartDate())) {
                        userSubscription.setStartDate(Instant.now());
                    }

                    userSubscription.setTimestamp(Instant.now());
                    userSubscription.setLastPaymentTime(Instant.now());
                    userSubscription.setCodes(List.of(CodeUtils.createRandomCode()));
                    return userSubscriptionRepository.save(userSubscription)
                            .then(sendTransfer(charge, payment, userSubscription.getGroupKey(), netAmount))
                            .flatMap(orderId -> {
                                payment.setStripeOrderId(orderId);
                                return paymentRepository.save(payment);
                            })
                            .flatMap(newPayment -> emailSendingService.sendEmails(userSubscription)
                                    .then(Mono.just(newPayment))
                            );
                });

    }

    private void updatePayment(long netAmount, Charge charge, Payment payment) {

        payment.setChargeStripeId(charge.getId());
        payment.setAmountAfterStripCommission(BigDecimal.valueOf(netAmount / 100));
        payment.setTimestamp(Instant.now());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setCurrency(Currency.valueOf(charge.getCurrency().toUpperCase(Locale.ROOT)));
        payment.setPaymentIntentStripeId(charge.getPaymentIntent());
        payment.setPaymentMethodStripeId(charge.getPaymentMethod());
        payment.setCustomerStripeId(charge.getCustomer());
    }


    private Mono<String> sendTransfer(Charge charge, Payment payment, String groupKey, long netAmount) {
        log.info("sendTransfer: chargeId={}, paymentKey={}", charge.getId(), payment.getId().toHexString());
        return stripeService.updatePaymentIntent(payment.getPaymentIntentStripeId())
                .flatMap(orderId -> groupsService.getStripeAccountGroupInfo(groupKey)
                            .flatMap(adminStripeInfo -> {
                                long adminAmount = (long) (netAmount * (1 - Double.parseDouble(stripeConfiguration.getOrbisCommission())));
                                return createTransfer(GROUP_ADMIN, payment, adminAmount, adminStripeInfo.getMainAdmin(), adminStripeInfo.getStripeId(), orderId)
                                        .then(Mono.defer(() -> {
                                            if (Objects.nonNull(adminStripeInfo.getPartnerKey())) {
                                                long partnerAmount = (long) (netAmount * Double.parseDouble(stripeConfiguration.getPartnerCommission()));
                                                return partnerService.getPartnerStripeInfo(adminStripeInfo.getPartnerKey())
                                                                .flatMap(partnerStripeInfo ->
                                                                        createTransfer(PARTNER, payment, partnerAmount, partnerStripeInfo.getUserKey(), partnerStripeInfo.getStripeId(), orderId)
                                                                )
                                                        .then(Mono.just(orderId));
                                            } else {
                                                return Mono.just(orderId);
                                            }
                                        }));
                            })
                );
    }

    public Mono<Void> createNewUpdateSubscriptionPayment(UserSubscription userSubscription, com.stripe.model.Subscription subscription) {
        return createSubscriptionPaymentFromSubscription(userSubscription, subscription)
                .then();
    }

    public Mono<StripeSecretDto> createFirstPaymentAndReturnCustomerSecret(
            FirstPaymentResult subscriptionResult,
            String userSubscriptionKey,
            String customerStripeId,
            Subscription subscription
    ) {
        var id = new ObjectId();
        var paymentBuilder = Payment.builder()
                .paymentId(id.toHexString())
                .userSubscriptionKey(userSubscriptionKey)
                .amount(subscription.getPrice())
                .currency(subscription.getCurrency())
                .status(PaymentStatus.INCOMPLETE)
                .paymentIntentStripeId(subscriptionResult.getPaymentIntentId())
                .invoiceStripeId(subscriptionResult.getInvoiceId())
                .customerStripeId(customerStripeId)
                .timestamp(Instant.now())
                .createTimestamp(Instant.now());

        if (subscription.getType().equals(SubscriptionType.ONE_TIME)) {
            paymentBuilder.paymentType(PaymentType.ONE_TIME_PAYMENT);
        } else {
            paymentBuilder.paymentType(PaymentType.SUBSCRIPTION_PAYMENT);
        }

        var payment = paymentBuilder.build();
        payment.setId(id);

        return paymentRepository.save(payment)
                .then(Mono.just(
                        StripeSecretDto.builder()
                                .clientSecret(subscriptionResult.getPaymentClientSecret())
                                .publicToken(stripeConfiguration.getStripePublicToken())
                                .build())
                );
    }

    private Mono<Payment> createSubscriptionPaymentFromCharge(Charge charge) {
        var id = new ObjectId();
        var payment = Payment.builder()
                .paymentId(id.toHexString())
                .amountAfterStripCommission(new BigDecimal(charge.getAmount() / 100))
                .currency(Currency.valueOf(charge.getCurrency().toUpperCase(Locale.ROOT)))
                .paymentType(PaymentType.SUBSCRIPTION_PAYMENT)
                .status(PaymentStatus.SUBSCRIPTION_INCOMPLETE)
                .chargeStripeId(charge.getId())
                .invoiceStripeId(charge.getInvoice())
                .paymentIntentStripeId(charge.getPaymentIntent())
                .paymentMethodStripeId(charge.getPaymentMethod())
                .timestamp(Instant.now())
                .createTimestamp(Instant.now())
                .build();
        payment.setId(id);
        return paymentRepository.save(payment);
    }

    private Mono<Payment> createSubscriptionPaymentFromSubscription(UserSubscription userSubscription, com.stripe.model.Subscription subscription) {
        var id = new ObjectId();
        var payment = Payment.builder()
                .paymentId(id.toHexString())
                .userSubscriptionKey(userSubscription.getUserSubscriptionKey())
                .paymentType(PaymentType.SUBSCRIPTION_PAYMENT)
                .status(PaymentStatus.SUBSCRIPTION_INCOMPLETE)
                .invoiceStripeId(subscription.getLatestInvoice())
                .timestamp(Instant.now())
                .createTimestamp(Instant.now())
                .build();
        payment.setId(id);
        return paymentRepository.save(payment);
    }

    private Mono<Void> createTransfer(StripeTransferType type, Payment payment, long amount, String userKey, String stripeId, String orderId) {
        var id = new ObjectId();
        var transfer = StripeTransfer.builder()
                .transferStripeKey(id.toHexString())
                .paymentId(payment.getPaymentId())
                .userKey(userKey)
                .stripeAccountId(stripeId)
                .type(type)
                .amount(BigDecimal.valueOf((double)amount / 100))
                .currency(payment.getCurrency())
                .stripeOrderId(orderId)
                .timestamp(Instant.now())
                .createTimestamp(Instant.now())
                .build();
        return stripeService.createTransfer(amount, payment.getCurrency(), stripeId, payment.getChargeStripeId())
                .flatMap(transferStripId -> {
                    transfer.setTransferStripId(transferStripId);
                    return stripeTransferRepository.save(transfer);
                })
                .then();
    }
}
