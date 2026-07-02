package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.UserSubscriptionStatus;
import to.orbis.v2.backend.models.entity.UserSubscription;

public interface UserSubscriptionRepository extends ReactiveMongoRepository<UserSubscription, ObjectId> {
    Mono<UserSubscription> findBySubscriptionKeyAndUserKeyAndStatus(String subscriptionKey, String userKey, UserSubscriptionStatus status);

    Flux<UserSubscription> findAllByGroupKeyAndStatus(String groupKey, UserSubscriptionStatus status);

    Mono<UserSubscription> findByUserSubscriptionKey(String userSubscriptionKey);

    Mono<UserSubscription> findBySubscriptionStripeId(String subscriptionStripeId);
}
