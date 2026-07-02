package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import to.orbis.v2.backend.models.entity.StripeTransfer;

public interface StripeTransferRepository extends ReactiveMongoRepository<StripeTransfer, ObjectId> {
}
