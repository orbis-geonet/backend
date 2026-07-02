package to.orbis.v2.backend.models.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import to.orbis.v2.backend.models.PlaceType;

import java.time.Instant;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class SimplifiedPlace {
    GeoJsonPoint coordinates;
    String name;
    String placeKey;
    PlaceType type;
    String source;
    String address;
    String description;
    String csvHash;
    String csvUrl;
    String phone;
    Boolean deleted;
    Instant timestamp;
    Instant createTimestamp;
    String googlePlaceId;
    String imageName;
    String shareLink;
    String fullShareLink;
    PlaceAddress googleAddress;
    String slug;
}
