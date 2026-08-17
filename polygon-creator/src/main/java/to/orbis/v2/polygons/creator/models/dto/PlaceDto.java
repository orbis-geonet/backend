package to.orbis.v2.polygons.creator.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PlaceDto {
    PointDto coordinates;
    String name;
    String placeKey;
    Instant lastCheckInTimestamp;
    Instant lastSizeChangeTimestamp;
    String dominantGroupKey;
    Instant creationServerTimestamp;
    Instant timestamp;
    Instant createTimestamp;
    String groupCreatedKey;
    double size;
    double lastSize;
}
