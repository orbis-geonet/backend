package to.orbis.v2.polygons.creator.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;
import to.orbis.v2.polygons.creator.models.entity.PolygonSchedulerCoordinate;

import java.time.Instant;
import java.util.List;

@Repository
public interface PolygonSchedulerCoordinateRepository extends MongoRepository<PolygonSchedulerCoordinate, ObjectId> {

    List<PolygonSchedulerCoordinate> findAllByIsEnabledAndAndTypeAndStatus(
            boolean isEnable,
            PolygonSchedulerCoordinateType type,
            PolygonSchedulerCoordinateStatus status,
            PageRequest pageRequest
    );

    void deleteAllByStatusAndCreatedAtBefore(PolygonSchedulerCoordinateStatus status, Instant createdAt);
}
