package to.orbis.v2.backend.models.dto;

import lombok.Data;
import to.orbis.v2.backend.models.PostType;

import java.time.Instant;
import java.util.List;

@Data
public class PostDto {
    PointDto coordinates;
    String postKey;
    Instant timestamp;
    Instant createTimestamp;
    PostType type;
    String title;
    String details;
    String address;
    Instant plannedTime;
    Instant plannedEndTime;
    RichLinkDataDto richLinkData;
    List<String> mediaUrls;
    List<String> signedUrls;
    String city;

    SimplifiedGroupDto group;
    PlaceDto place;
    SimplifiedUserDto user;

    Double dist;
    Boolean seen;

    String shareLink;
    String fullShareLink;
    boolean attending;

    int commentsCount;
    int likesCount;
    int confirmedCount;
    boolean userLiked;

    Instant lastSeen;
    String checkInPolygonCoordinateKey;
}
