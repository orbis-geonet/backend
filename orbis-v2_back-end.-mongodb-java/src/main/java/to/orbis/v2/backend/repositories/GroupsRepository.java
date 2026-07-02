package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Group;

public interface GroupsRepository extends ReactiveMongoRepository<Group, ObjectId> {

    Flux<Group> findByLocationNearAndDeletedFalse(GeoJsonPoint point, Distance distance, Pageable pageable);

    Mono<Group> findByNameAndDeletedFalse(String name);
    Mono<Group> findBySlug(String slug);

    Flux<Group> findAllBySlug(String slug);

    Mono<Group> findOneByGroupKeyAndDeletedFalse(String groupKey);

    Mono<Long> countByName(String name);

    Mono<Long> countByEmptySlug(String emptySlug);

    Mono<Long> countByMainAdminAndDeletedFalseAndIsSubscriptionActivateTrue(String mainAdminKey);
}
