package to.orbis.v2.polygons.creator.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.polygons.creator.models.entity.PolygonSchedulerCoordinate;

import java.time.Instant;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PolygonSchedulerCoordinateAggregationRepository {
    private final MongoTemplate mongoTemplate;

    public long updateStatus(Set<String> coordinateKeys, PolygonSchedulerCoordinateStatus status) {
        Query query = new Query();
        query.addCriteria(Criteria.where(PolygonSchedulerCoordinate.Fields.polygonSchedulerCoordinateKey.name()).in(coordinateKeys));

        return updateStatus(query, status);
    }

    public void updateStatusPreDestroy(Set<String> coordinateKeys, PolygonSchedulerCoordinateStatus status) {
        Query query = new Query();
        query.addCriteria(Criteria.where(PolygonSchedulerCoordinate.Fields.polygonSchedulerCoordinateKey.name()).in(coordinateKeys));
        query.addCriteria(Criteria.where(PolygonSchedulerCoordinate.Fields.status.name())
                .ne(PolygonSchedulerCoordinateStatus.NEW));

        updateStatus(query, status);
    }

    private long updateStatus(Query query, PolygonSchedulerCoordinateStatus status) {
        Update update = new Update();
        update.set(PolygonSchedulerCoordinate.Fields.status.name(), status);

        var result = mongoTemplate.updateMulti(query, update, PolygonSchedulerCoordinate.class);
        return result.getMatchedCount();
    }

    public void updateStatusForCoordinateAround(
            GeoJsonPoint point, PolygonSchedulerCoordinateStatus status) {
        Query query = new Query(Criteria
                .where(PolygonSchedulerCoordinate.Fields.coordinates.name())
                .near(point)
                .maxDistance(3 / 6378.1));

        Update update = new Update();
        update.set(PolygonSchedulerCoordinate.Fields.status.name(), status);
        update.set(PolygonSchedulerCoordinate.Fields.finishedAt.name(), Instant.now());

        mongoTemplate.updateMulti(query, update, PolygonSchedulerCoordinate.class);
    }
}
