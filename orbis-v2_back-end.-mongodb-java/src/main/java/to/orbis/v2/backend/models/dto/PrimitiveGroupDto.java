package to.orbis.v2.backend.models.dto;

import lombok.Data;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

@Data
public class PrimitiveGroupDto {
    String groupKey;
    String name;
    PointDto location;
    String imageName;
}
