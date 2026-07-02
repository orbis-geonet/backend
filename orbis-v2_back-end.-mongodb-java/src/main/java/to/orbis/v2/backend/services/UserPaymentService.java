package to.orbis.v2.backend.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.SubscriptionException;
import to.orbis.v2.backend.models.entity.Subscription;
import to.orbis.v2.backend.repositories.SubscriptionAggregationRepository;

import java.util.Objects;

@Slf4j
@AllArgsConstructor
public abstract class UserPaymentService {
    protected final SubscriptionAggregationRepository subscriptionAggregationRepository;


    protected Mono<Subscription> checkPurchaseCurrency(Subscription subscription, String userKey) {
        return subscriptionAggregationRepository.countSubscriptionWithOtherCurrency(subscription.getCurrency(), userKey)
                .flatMap(count -> {
                    if (count.getResult() > 0) {
                        return Mono.error(() -> new SubscriptionException("There is a subscription with different currency. One user cannot use several currencies."));
                    } else {
                        return Mono.just(subscription);
                    }
                });
    }

    protected Mono<Subscription> validatePurchaseBeforePayment(Subscription subscription) {
        if (Objects.isNull(subscription.getStripeProductId()) || subscription.getStripeProductId().isEmpty()) {
            return Mono.error(() -> new SubscriptionException("There is no product in Stripe for this subscription."));
        } else if (Objects.isNull(subscription.getStripePriceId()) || subscription.getStripePriceId().isEmpty()){
            return Mono.error(() -> new SubscriptionException("There is no prise in Stripe for this subscription."));
        } else {
            return Mono.just(subscription);
        }
    }

    protected Mono<Subscription> getNoFoundError() {
        return Mono.error(() -> new NoDataFoundException("Subscription not found."));
    }
}
