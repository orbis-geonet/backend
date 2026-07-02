package to.orbis.v2.backend.services;

import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.StripeConfiguration;
import to.orbis.v2.backend.exceptions.StripeException;
import to.orbis.v2.backend.models.StripeSubscriptionEventType;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {
    private final StripeConfiguration stripeConfiguration;
    private final StripeAccountService stripeAccountService;
    private final StripePaymentService stripePaymentService;
    private final SubscriptionsService subscriptionsService;

    public Mono<Void> getConnectInformation(String payload, String sigHeader) {
        return getStripeEvent(payload, sigHeader, stripeConfiguration.getStripeConnectWebhookSecret())
                .flatMap(event -> {
                     if (event.getType().equals("account.updated")) {
                        return getStripeObject(event)
                                .flatMap(stripeObject -> Mono.just((Account) stripeObject));
                    } else {
                         return Mono.error(() -> new StripeException("Event type should be account.updated"));
                    }
                })
                .flatMap(account -> {
                    log.info("getConnectInformation: id {}", account.getId());
                    return stripeAccountService.updateAccountFromWebhook(
                            account.getId(),
                            account.getPayoutsEnabled(),
                            account.getRequirements().getPastDue()
                            );
                })
                .then();
    }

    public Mono<Void> getPaymentInfo(String payload, String sigHeader) {
        return getStripeEvent(payload, sigHeader, stripeConfiguration.getStripePaymentWebhookSecret())
                .flatMap(event -> {
                    if ("charge.succeeded".equals(event.getType())) {
                        return getStripeObject(event)
                                .flatMap(stripeObject -> Mono.just((Charge) stripeObject))
                                .flatMap(charge -> {
                                    log.info("getPaymentInfo: charge.succeeded id {}", charge.getId());
                                    return stripePaymentService.updatePaymentFromStripe(charge);
                                });
                    } else {
                        return Mono.error(() -> new StripeException("Event type should be charge.succeeded"));
                    }
                });
    }

    public Mono<Void> getSubscriptionInformation(String payload, String sigHeader) {
        return getStripeEvent(payload, sigHeader, stripeConfiguration.getStripeSubscriptionWebhookSecret())
                .flatMap(event -> {
                    switch (event.getType()) {
                        case "customer.subscription.updated":
                        case "customer.subscription.deleted":
                            return Mono.just(event);
                        default:
                            return Mono.error(() -> new StripeException("Event type should be charge.succeeded"));
                    }
                })
                .flatMap(event -> {
                    var eventType = StripeSubscriptionEventType.getEvent(event.getType());
                    return getStripeObject(event)
                            .flatMap(stripeObject -> Mono.just((Subscription) stripeObject))
                            .flatMap(subscription -> {
                                switch (eventType) {
                                    case UPDATED:
                                        return subscriptionsService.submitUpdateSubscriptionFromStripeWebhook(subscription);
                                    case DELETED:
                                        return subscriptionsService.submitDeleteSubscriptionEventFromStripeWebhook(subscription);
                                    default:
                                        return Mono.error(() -> new IllegalStateException("Unexpected value: " + eventType));
                                }
                            });
                });
    }

    public Mono<Event> getStripeEvent(String payload, String sigHeader, String endpointSecret) {
        try {
            var event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            log.debug("getStripeEvent: get event type {}", event.getType());
            return Mono.just(event);
        } catch (JsonSyntaxException e) {
            log.error("Invalid payload: message={} payload={}", e.getMessage(), payload);
            return Mono.error(() -> new StripeException("Invalid payload"));
        } catch (SignatureVerificationException e) {
            log.error("Invalid signature: message={} sigHeader={} endpointSecret={}", e.getMessage(), sigHeader, endpointSecret);
            return Mono.error(() -> new StripeException("Invalid signature"));
        }
    }

    public Mono<StripeObject> getStripeObject(Event event) {
        var dataObjectDeserializer = event.getDataObjectDeserializer();
        if (dataObjectDeserializer.getObject().isPresent()) {
            var stripeObject = dataObjectDeserializer.getObject();
            return stripeObject.map(Mono::just)
                    .orElseGet(() -> Mono.error(() -> new StripeException("Deserialization failed, probably due to an API version mismatch")));
        } else {
            return Mono.error(() -> new StripeException("Deserialization failed, probably due to an API version mismatch"));
        }
    }
}
