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
import to.orbis.v2.backend.models.entity.PlaceRate;

@Repository
public interface PlaceRateRepository extends ReactiveMongoRepository<PlaceRate, ObjectId> {
    Mono<PlaceRate> findByPlaceKeyAndUserKey(String placeKey, String userKey);
}
