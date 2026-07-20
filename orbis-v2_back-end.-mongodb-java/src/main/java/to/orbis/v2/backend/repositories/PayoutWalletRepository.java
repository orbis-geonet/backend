package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.PayoutWallet;

public interface PayoutWalletRepository extends ReactiveMongoRepository<PayoutWallet, ObjectId> {
    Mono<PayoutWallet> findByUserKey(String userKey);
}
