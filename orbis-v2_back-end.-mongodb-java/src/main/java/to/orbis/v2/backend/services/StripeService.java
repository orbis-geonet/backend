package to.orbis.v2.backend.services;

import com.stripe.Stripe;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.StripeConfiguration;
import to.orbis.v2.backend.exceptions.StripeException;
import to.orbis.v2.backend.models.*;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.dto.stripe.FirstPaymentResult;
import to.orbis.v2.backend.models.entity.Subscription;
import to.orbis.v2.backend.models.entity.User;
import to.orbis.v2.backend.models.entity.UserSubscription;
import to.orbis.v2.backend.repositories.StripeAccountRepository;
import to.orbis.v2.backend.utils.SupplierWithStripeException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {
    private final StripeConfiguration stripeConfiguration;
    private final StripeAccountRepository stripeAccountRepository;

    public Mono<String> createAccount(
            Country country,
            BusinessType businessType
    ) {
        var accountCreateParams = AccountCreateParams.builder()
                .setCountry(country.name())
                .setBusinessType(BusinessType.getBusinessType(businessType))
                .setType(AccountCreateParams.Type.CUSTOM)
                .setCapabilities(
                        AccountCreateParams.Capabilities.builder()
                                .setTransfers(
                                        AccountCreateParams.Capabilities.Transfers.builder()
                                                .setRequested(true)
                                                .build())
                                .setCardPayments(
                                        AccountCreateParams.Capabilities.CardPayments.builder()
                                                .setRequested(true)
                                                .build())
                                .build()
                ).build();


        return process(() -> Account.create(accountCreateParams), "Cannot create account.")
                .flatMap(account -> {
                    log.info("createAccount: accountId: {}", account.getId());
                    return Mono.just(account.getId());
                });
    }

    public Mono<Subscription> createSubscriptionAsProduct(Subscription subscription) {
        var params =
                ProductCreateParams
                        .builder()
                        .setName("Product for subscription key: " + subscription.getSubscriptionKey())
                        .addExpand("default_price")
                        .build();

        return process(() -> Product.create(params), "Cannot create product.")
                .flatMap(product -> {
                    log.info("createSubscriptionAsProduct: productId: {}", product.getId());
                    subscription.setStripeProductId(product.getId());
                    return Mono.just(subscription);
                })
                .flatMap(sub -> createPrice(subscription));
    }

    public Mono<Subscription> updatePrice(Subscription subscription) {
        return archivePrice(subscription)
                .then(createPrice(subscription));
    }

    public Mono<Subscription> createPrice(Subscription subscription) {
        return process(() -> {
            var interval = PriceCreateParams.Recurring.Interval.MONTH;
            if (Objects.nonNull(subscription.getInterval()) && subscription.getInterval().equals(SubscriptionInterval.YEAR)) {
                interval = PriceCreateParams.Recurring.Interval.YEAR;
            }
            PriceCreateParams.Builder params =
                    PriceCreateParams
                            .builder()
                            .setProduct(subscription.getStripeProductId())
                            .setUnitAmount((long)(subscription.getPrice().doubleValue() * 100))
                            .setCurrency(subscription.getCurrency().name());

            if (Objects.isNull(subscription.getType()) || !subscription.getType().equals(SubscriptionType.ONE_TIME)) {
                params.setRecurring(
                        PriceCreateParams.Recurring
                                .builder()
                                .setInterval(interval)
                                .build()
                );
            }

            if (subscription.getType().equals(SubscriptionType.INTERVAL) && (Objects.isNull(subscription.getPeriod()) || subscription.getPeriod() < 2)) {
                throw new StripeException("Cannot create INTERVAL subscription with interval less 2");
            }

            Price priceNew = Price.create(params.build());
            subscription.setStripePriceId(priceNew.getId());
            return subscription;
        }, "Cannot create a new price. ");
    }

    public Mono<Void> archivePrice(Subscription subscription) {
        return process(() -> {
                    Price resource = Price.retrieve(subscription.getStripePriceId());
                    var paramsPriceArchive = PriceUpdateParams.builder().setActive(false).build();
                    resource.update(paramsPriceArchive);
                    return Mono.empty();
                },
                "Cannot archive price. ")
                .then();
    }

    public Mono<String> createAccountLinkOnboarding(String accountId) {
        return createAccountLink(accountId, AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING);
    }

    public Mono<String> createAccountLinkUpdate(String accountId) {
        return createAccountLink(accountId, AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING);
    }

    public Mono<String> createAccountLinkVerification(String accountId) {
        return createAccountLink(accountId, AccountLinkCreateParams.Type.CUSTOM_ACCOUNT_VERIFICATION);
    }

    private Mono<String> createAccountLink(String accountId, AccountLinkCreateParams.Type type) {
        var params =
                AccountLinkCreateParams
                        .builder()
                        .setAccount(accountId)
                        .setRefreshUrl(stripeConfiguration.getRedirectUrl())
                        .setReturnUrl(stripeConfiguration.getRedirectUrl())
                        .setType(type)
                        .setCollect(AccountLinkCreateParams.Collect.CURRENTLY_DUE)
                        .build();

        return process(() -> AccountLink.create(params), "Cannot create account link.")
                .flatMap(accountLink -> {
                    log.info("createAccountLink: url: {}, expiresAt: {}", accountLink.getUrl(), getTime(accountLink.getExpiresAt()));
                    return Mono.just(accountLink.getUrl());
                });
    }

    public Mono<String> createCustomer(String userKey) {
        var params = CustomerCreateParams.builder()
                .setDescription("Customer user with userKey: " + userKey)
                .build();
        return process(() -> {
            var customer = Customer.create(params);
            log.info("createCustomer: customer created. stripeId: {}", customer.getId());
            return customer.getId();
        }, "Cannot create customer. ");
    }

    public Mono<Void> unsubscribe(UserSubscription subscription) {
        return process(() -> {
            var subscriptionStripe = com.stripe.model.Subscription.retrieve(subscription.getSubscriptionStripeId());
            var deletedSubscription = subscriptionStripe.cancel();
            log.info("unsubscribe: deletedSubscriptionId {} subscriptionId {}", deletedSubscription.getId(), subscription.getSubscriptionStripeId());
            return deletedSubscription.getId();
        }, "Cannot unsubscribe. "
        ).then();
    }

    public Mono<FirstPaymentResult> subscribe(Subscription subscription, User user, User groupOwner) {
        return stripeAccountRepository.findByUserKeyAndDeletedFalse(groupOwner.getUserKey())
                .flatMap(stripeAccount -> {
                    SubscriptionScheduleCreateParams.Builder paramsBuilder =
                            SubscriptionScheduleCreateParams.builder()
                                    .setCustomer(user.getCustomerStripeId())
                                    .setStartDate(SubscriptionScheduleCreateParams.StartDate.NOW)
                                    .addAllExpand(List.of("subscription", "subscription.latest_invoice", "subscription.latest_invoice.payment_intent"));

                    SubscriptionScheduleCreateParams.Phase.Builder phaseBuilder = SubscriptionScheduleCreateParams.Phase.builder()
                            .addItem(
                                    SubscriptionScheduleCreateParams.Phase.Item.builder()
                                            .setPrice(subscription.getStripePriceId())
                                            .setQuantity(1L)
                                            .build()
                            );

                    switch (subscription.getType()) {
                        case UNLIMITED:
                            paramsBuilder.setEndBehavior(SubscriptionScheduleCreateParams.EndBehavior.RELEASE);
                            break;
                        case INTERVAL:
                            phaseBuilder.setIterations(Long.valueOf(subscription.getPeriod()));
                            paramsBuilder.setEndBehavior(SubscriptionScheduleCreateParams.EndBehavior.CANCEL);
                            break;
                        default:
                            return Mono.error(new StripeException("Cannot use subscription fro ONE_TIME"));
                    }

                    paramsBuilder.addPhase(phaseBuilder.build());

                    return Mono.just(paramsBuilder.build());
                })
                .flatMap(params -> process(() -> {
                    SubscriptionSchedule subscriptionSchedule = SubscriptionSchedule.create(params);
                    log.info("createSubscriptionScheduler: subscriptionScheduleId {}", subscriptionSchedule.getId());
                    return subscriptionSchedule.getSubscriptionObject();
                }, "Cannot create subscription"))
                .flatMap(stipeSubscription -> process(() -> {
                    Invoice resource = Invoice.retrieve(stipeSubscription.getLatestInvoice());
                    resource.finalizeInvoice(InvoiceFinalizeInvoiceParams.builder().build());

                    var stripeSubscriptionFinal = com.stripe.model.Subscription.retrieve(
                            stipeSubscription.getId(),
                            SubscriptionRetrieveParams.builder()
                                    .addAllExpand(List.of("latest_invoice.payment_intent"))
                                    .build(),
                            RequestOptions.getDefault()
                    );
                    return FirstPaymentResult.builder()
                            .stripeId(stripeSubscriptionFinal.getId())
                            .invoiceId(stripeSubscriptionFinal.getLatestInvoice())
                            .paymentIntentId(stripeSubscriptionFinal.getLatestInvoiceObject().getPaymentIntent())
                            .paymentClientSecret(stripeSubscriptionFinal.getLatestInvoiceObject().getPaymentIntentObject().getClientSecret())
                            .build();
                }, "Cannot finalise invoice"));
    }

    public Mono<String> updatePaymentIntent(String paymentIntentId) {
        return process(() -> {
                String orderId = new ObjectId().toHexString();
                PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("order_id", orderId);
                Map<String, Object> params = new HashMap<>();
                params.put("metadata", metadata);
//                intent.update(params);
                return orderId;
            }, "Cannot update payment intent. "
        );
    }

    public Mono<FirstPaymentResult> makeOneTimePayment(Subscription subscription, User user, Integer number) {
        return process(() -> {
            Long amount = (long)(subscription.getPrice().doubleValue() * 100) * number;
            PaymentIntentCreateParams params =
                    PaymentIntentCreateParams.builder()
                            .setAmount(amount)
                            .setCustomer(user.getCustomerStripeId())
                            .setCurrency(subscription.getCurrency().name())
                            .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);
            return FirstPaymentResult.builder()
                    .paymentIntentId(paymentIntent.getId())
                    .paymentClientSecret(paymentIntent.getClientSecret())
                    .build();
        }, "Cannot make one time payment");
    }

    public Mono<String> createTransfer(long amount, Currency currency, String stripeAccountId, String chargeId) {
        return process(() -> {
            TransferCreateParams params =
                    TransferCreateParams.builder()
                            .setAmount(amount)
                            .setCurrency(currency.name())
                            .setDestination(stripeAccountId)
                            .setSourceTransaction(chargeId)
                            .build();

            Transfer transfer = Transfer.create(params);
            log.info("createTransfer: transferId {}", transfer.getId());
            return transfer.getId();
            }, "Cannot create transfer. "
        );
    }

    public Mono<Invoice> getInvoice(String invoiceId) {
        return process(() ->  Invoice.retrieve(invoiceId), "Cannot get invoice. ");
    }

    private String getTime(long time) {
        Date currentDate = new Date(time);
        DateFormat df = new SimpleDateFormat("dd:MM:yy HH:mm:ss");

        return df.format(currentDate);
    }

    private <T> Mono<T> process(SupplierWithStripeException<T> supplier, String logErrorMessage) {
        try {
            Stripe.apiKey = stripeConfiguration.getStripeSecretToken();

            return Mono.just(supplier.getWithStripeException());
        } catch (com.stripe.exception.StripeException e) {
            log.error("process: error: {}", e.getStripeError().getMessage());
            return Mono.error(() -> new StripeException(logErrorMessage + "Stripe error. Message: " + e.getStripeError().getMessage()));
        } catch (Exception e) {
            log.error("process: error: {}", e.getMessage());
            return Mono.error(() -> new StripeException(logErrorMessage + "Error. Message: " + e.getMessage()));
        }
    }
}
