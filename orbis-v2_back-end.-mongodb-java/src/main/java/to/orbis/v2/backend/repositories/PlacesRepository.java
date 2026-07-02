package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Place;

@Repository
public interface PlacesRepository extends ReactiveMongoRepository<Place, ObjectId> {
    default Flux<Place> findAll(Pageable pageable) {
        return findByIdNotNull(pageable);
    }

    Flux<Place> findByIdNotNull(Pageable pageable);

    Flux<Place> findByCoordinatesNearAndLastCheckInTimestampExists(GeoJsonPoint point, Distance distance, boolean exists, Pageable pageable);

    Mono<Place> findOneByPlaceKey(String placeKey);

    Mono<Place> findFirstByUserCreatedKeyOrderByTimestampDesc(String userCreatedKey);

    Mono<Boolean> existsByGooglePlaceId(String googlePlaceId);

    Mono<Long> countByName(String name);

    Mono<Place> findBySlug(String slug);

    Flux<Place> findAllBySlug(String slug);

    Mono<Long> countByEmptySlug(String emptySlug);
}
