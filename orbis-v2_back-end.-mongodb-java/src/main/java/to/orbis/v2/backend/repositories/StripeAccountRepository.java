package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.StripeAccountStatus;
import to.orbis.v2.backend.models.entity.StripeAccount;

import java.util.List;

public interface StripeAccountRepository extends ReactiveMongoRepository<StripeAccount, ObjectId> {
    Mono<StripeAccount> findByUserKeyAndDeletedFalseAndStatusIn(String userKey, List<StripeAccountStatus> statuses);

    Mono<StripeAccount> findByUserKeyAndDeletedFalse(String userKey);

    Mono<StripeAccount> findByStripeId(String stripeId);
}
