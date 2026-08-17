package to.orbis.v2.polygons.creator.models.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.time.Instant;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class SimplifiedGroup {
    String groupKey;
    String name;
    GeoJsonPoint location;
    int colorIndex;
    String solidColorHex;
    String strokeColorHex;
    Instant timestamp;
    Instant createTimestamp;
}
