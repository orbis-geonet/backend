package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.PolygonSchedulerCoordinate;

@Repository
public interface PolygonSchedulerCoordinateRepository extends ReactiveMongoRepository<PolygonSchedulerCoordinate, ObjectId> {

    Mono<PolygonSchedulerCoordinate> findByPolygonSchedulerCoordinateKey(String key);
}
