package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.IgLink;

import java.time.Instant;

@Repository
public interface IgRepository extends ReactiveMongoRepository<IgLink, ObjectId> {

    Mono<IgLink> findByUserKeyEqualsAndExpirationTimeAfter(String userKey, Instant instant);
    Mono<IgLink> findByStateEquals(String state);
    Mono<Void> deleteAllByUserKeyEqualsAndIdNot(String userKey, ObjectId id);

    Mono<Void> deleteAllByIgUserId(Long igUserId);

    Mono<IgLink> findFirstByIgUserId(Long igUserId);
}
