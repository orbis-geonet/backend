package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Partner;

import java.util.Optional;

public interface PartnerRepository extends ReactiveMongoRepository<Partner, ObjectId> {
    Mono<Partner> findByUserKey(String userKey);

    Mono<Partner> findByPartnerKey(String partnerKey);
}
