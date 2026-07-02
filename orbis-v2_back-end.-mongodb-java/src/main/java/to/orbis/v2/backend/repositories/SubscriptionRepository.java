package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Subscription;

public interface SubscriptionRepository extends ReactiveMongoRepository<Subscription, ObjectId> {
    Mono<Subscription> findOneBySubscriptionKey(String subscriptionKey);

    Mono<Subscription> findOneBySubscriptionKeyAndDeletedFalse(String subscriptionKey);

    Flux<Subscription> findAllByGroupKeyAndDeletedFalse(String groupKey, Pageable pageable);
}
