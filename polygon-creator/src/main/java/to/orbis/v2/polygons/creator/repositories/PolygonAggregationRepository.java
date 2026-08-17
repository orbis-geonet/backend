package to.orbis.v2.polygons.creator.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.polygons.creator.models.entity.Polygon;
import to.orbis.v2.polygons.creator.models.entity.PolygonSchedulerCoordinate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PolygonAggregationRepository {
    private final MongoTemplate mongoTemplate;
    private static final int BATCH_SIZE = 500;

    @Transactional
    public void deleteByKeys(List<String> keys) {
        Query query = new Query();
        query.addCriteria(Criteria.where(PolygonSchedulerCoordinate.Fields.polygonSchedulerCoordinateKey.name()).in(keys));

        mongoTemplate.remove(query, Polygon.class);
    }

    @Transactional
    public void saveAll(List<Polygon> polygonsToSave) {
        for (int i = 0; i < polygonsToSave.size(); i += BATCH_SIZE) {
            List<Polygon> batch = polygonsToSave.subList(i, Math.min(i + BATCH_SIZE, polygonsToSave.size()));

            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Polygon.class);
            // Upsert by the deterministic polygonKey so that re-computing the same place-set (locally or
            // on another clone) updates the existing polygon in place instead of inserting a duplicate.
            for (Polygon polygon : batch) {
                Query query = new Query(Criteria.where("polygonKey").is(polygon.getPolygonKey()));
                Update update = new Update()
                        .setOnInsert("_id", polygon.getId())
                        .set("polygonKey", polygon.getPolygonKey())
                        .set("groupKey", polygon.getGroupKey())
                        .set("placeKeys", polygon.getPlaceKeys())
                        .set("polygonCenter", polygon.getPolygonCenter())
                        .set("polygonPoints", polygon.getPolygonPoints())
                        .set("createdAt", polygon.getCreatedAt());
                bulkOps.upsert(query, update);
            }
            bulkOps.execute();
        }
    }
}
