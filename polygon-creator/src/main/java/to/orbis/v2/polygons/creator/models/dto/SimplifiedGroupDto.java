package to.orbis.v2.polygons.creator.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = false)
public class SimplifiedGroupDto {
    String groupKey;
    String name;
    PointDto location;
    String solidColorHex;
    String strokeColorHex;
    Instant timestamp;
    Instant createTimestamp;
}
