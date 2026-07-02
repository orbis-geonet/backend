package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Checkin;

import java.time.Instant;

@Repository
public interface CheckinRepository extends ReactiveMongoRepository<Checkin, ObjectId> {

    default Mono<Checkin> findLastValidCheckinAfterTimestamp(String userKey, String placeKey, Instant timestamp) {
        return findFirstByUserKeyAndPlaceKeyAndValidTimestampGreaterThanEqualOrderByValidTimestampDesc(userKey, placeKey, timestamp);
    }

    Mono<Checkin> findFirstByUserKeyAndPlaceKeyAndValidTimestampGreaterThanEqualOrderByValidTimestampDesc(String userKey, String placeKey, Instant timestamp);

    Mono<Void> deleteAllByPlaceKeyAndGroupKey(String placeKey, String groupKey);

    Mono<Void> deleteAllByGroupKey(String groupKey);
}
