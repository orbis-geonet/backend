package to.orbis.v2.backend.models.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

@Data
public class PrimitiveUser {
    String userKey;
    String displayName;
    String providerImageUrl;
    String imageName;
}

