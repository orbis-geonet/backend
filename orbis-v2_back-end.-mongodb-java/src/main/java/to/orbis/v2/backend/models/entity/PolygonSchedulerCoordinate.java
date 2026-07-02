package to.orbis.v2.backend.models.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.backend.models.PolygonSchedulerCoordinateType;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
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
}