package to.orbis.v2.backend.models.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    boolean deleted;
    boolean reported;

    String shareLink;
    String fullShareLink;
    String slug;

    @JsonProperty
    public RankDiffType getRankDiffType() {
        if (postsLastWeek == null || postsThisWeek == null || postsThisWeek.equals(postsLastWeek)) {
            return RankDiffType.LEVEL;
        }

        if (postsLastWeek == 0 && postsThisWeek > 50) {
            return RankDiffType.FIRE;
        }

        double increase = (postsThisWeek - postsLastWeek) * 1.0 / postsLastWeek;

        if (increase >= 1) {
            return RankDiffType.FIRE;
        }

        if (postsLastWeek < postsThisWeek) {
            return RankDiffType.RISING;
        } else {
            return RankDiffType.FALLING;
        }
    }

    Integer postsThisWeek;
    Integer postsLastWeek;


}
