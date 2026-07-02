package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.StripeException;
import to.orbis.v2.backend.services.StripeWebhookService;

import java.util.Objects;

@Slf4j
@Validated
@RestController
@RequestMapping("/stripe/webhook")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "stripe", name = "webhook-enable", havingValue = "true")
public class StripeWebhookController {
    private final StripeWebhookService stripeWebhookService;

    //https://orbis-v2.rj.r.appspot.com/stripe/webhook

    // topic from Stripe:
    // account.updated
    @PostMapping("/connect")
    @PreAuthorize("permitAll")
    public Mono<Void> getConnectInformation(
            @RequestBody String payload,
            ServerWebExchange exchange
    ) {
        return getStripeHeader(exchange)
                .flatMap(header -> {
                    log.debug("getConnectInformation: payload: {}, stripeHeaders: {}", payload, header);
                    return stripeWebhookService.getConnectInformation(payload, header);
                });
    }

    // topic from Stripe:
    // payment_intent.canceled
    // charge.succeeded
    @PostMapping("/payment")
    @PreAuthorize("permitAll")
    public Mono<Void> getPaymentInformation (
            @RequestBody String payload,
            ServerWebExchange exchange
    ) {
        return getStripeHeader(exchange)
                .flatMap(header -> {
                    log.debug("getPaymentInformation: payload: {}, stripeHeaders: {}", payload, header);
                    return stripeWebhookService.getPaymentInfo(payload, header);
                });
    }

    // topic from Stripe:
    // customer.subscription.created
    // customer.subscription.updated
    // customer.subscription.deleted
    @PostMapping("/subscription")
    @PreAuthorize("permitAll")
    public Mono<Void> getSubscriptionInformation (
            @RequestBody String payload,
            ServerWebExchange exchange
    ) {
        return getStripeHeader(exchange)
                .flatMap(header -> {
                    log.debug("getSubscriptionInformation: payload: {}, stripeHeaders: {}", payload, header);
                    return stripeWebhookService.getSubscriptionInformation(payload, header);
                });
    }

    public Mono<String> getStripeHeader(ServerWebExchange exchange) {
        var stripeHeaders = exchange.getRequest().getHeaders().get("Stripe-Signature");
        if (Objects.isNull(stripeHeaders) || stripeHeaders.isEmpty()) {
            return Mono.error(() -> new StripeException("stripeHeaders is empty"));
        }
        return Mono.just(stripeHeaders.get(0));
    }
}
