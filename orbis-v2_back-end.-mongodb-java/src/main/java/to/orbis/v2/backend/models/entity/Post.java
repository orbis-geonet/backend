package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.PostType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "posts")
@FieldNameConstants(asEnum = true)
public class Post extends Entity {
    GeoJsonPoint coordinates;
    String postKey;
    Instant timestamp;
    Instant createTimestamp;
    PostType type;
    String groupKey;
    String placeKey;
    String userKey;
    String title;
    String details;
    Instant plannedTime;
    Instant plannedEndTime;
    RichLinkData richLinkData;
    List<String> mediaUrls;
    Set<String> liked;
    boolean deleted;
    String address;
    String shareLink;
    String fullShareLink;
    String checkInPolygonCoordinateKey;
    String city;

    Boolean reported;
    String reportedMessage;
    Boolean reportedSolved;
    Instant reportedTime;

    public int getLikesCount() {
        if (getLiked() == null) return 0;
        return getLiked().size();
    }
}
