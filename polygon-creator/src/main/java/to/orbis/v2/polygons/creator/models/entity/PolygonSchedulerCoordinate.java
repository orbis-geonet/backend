package to.orbis.v2.polygons.creator.models.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@FieldNameConstants(asEnum = true)
@Document(collection = "polygonSchedulerCoordinate")
public class PolygonSchedulerCoordinate {
    ObjectId id;
    String polygonSchedulerCoordinateKey;
    double longitude;
    double latitude;
    GeoJsonPoint coordinates;
    double radius;
    PolygonSchedulerCoordinateType type;
    PolygonSchedulerCoordinateStatus status;
    boolean isEnabled;
    boolean isCalculated;
    Integer numberOfPlaces;
    Integer numberOfPolygons;
    Instant createdAt;
    Instant finishedAt;
    Long timeInSec;
}