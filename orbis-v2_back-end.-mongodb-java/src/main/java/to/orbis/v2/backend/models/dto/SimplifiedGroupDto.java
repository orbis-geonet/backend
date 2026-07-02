package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import to.orbis.v2.backend.models.entity.RankDiffType;

import java.time.Instant;

@Data
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = false)
public class SimplifiedGroupDto {
    String groupKey;
    String name;
    PointDto location;
    String description;
    String imageName;
    int colorIndex;
    String solidColorHex;
    String strokeColorHex;
    Instant timestamp;
    Instant createTimestamp;
    String os;
    Integer placesCount;
    Integer rank;
    Integer rankDiff;
    RankDiffType rankDiffType;
    boolean deleted;

    String shareLink;
    String fullShareLink;
    String slug;
}
