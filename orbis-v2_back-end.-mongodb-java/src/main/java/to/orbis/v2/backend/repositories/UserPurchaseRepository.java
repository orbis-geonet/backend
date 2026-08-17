package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.UserSubscriptionStatus;
import to.orbis.v2.backend.models.entity.UserPurchase;

public interface UserPurchaseRepository extends ReactiveMongoRepository<UserPurchase, ObjectId> {
    Mono<UserPurchase> findByUserPurchaseKey(String userPurchaseKey);

    Mono<UserPurchase> findByPaymentRef(String paymentRef);

    Flux<UserPurchase> findAllByPurchaseKeyAndUserKeyAndStatus(String purchaseKey, String userKey, UserSubscriptionStatus status);
}
