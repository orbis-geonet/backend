package to.orbis.v2.backend.models.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

@Data
public class PrimitiveGroup {
    String groupKey;
    String name;
    GeoJsonPoint location;
    String imageName;
}
