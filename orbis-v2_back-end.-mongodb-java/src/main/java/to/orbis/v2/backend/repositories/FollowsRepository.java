package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Follow;

@Repository
public interface FollowsRepository extends ReactiveMongoRepository<Follow, ObjectId> {

    Mono<Follow> findFirstByFollowerKeyAndUserKey(String followerKey, String userKey);
    Mono<Follow> findFirstByFollowerKeyAndPlaceKey(String followerKey, String placeKey);
    Mono<Follow> findFirstByFollowerKeyAndGroupKey(String followerKey, String groupKey);

    Mono<Void> deleteByFollowerKeyAndPlaceKey(String followerKey, String placeKey);
    Mono<Void> deleteByFollowerKeyAndUserKey(String followerKey, String userKey);
    Mono<Void> deleteByFollowerKeyAndGroupKey(String followerKey, String groupKey);
    Mono<Void> deleteAllByFollowerKeyAndUserKeyAndAcceptedFalse(String followerKey, String userKey);

    Flux<Follow> findAllByFollowerKeyAndAcceptedTrue(String followerKey);

    Mono<Boolean> existsByFollowerKeyAndPlaceKey(String followerKey, String placeKey);

    Flux<Follow> findAllBy(Criteria criteria);

    Mono<Long> countByUserKeyAndAcceptedFalseAndSeenFalse(String userKey);
}
