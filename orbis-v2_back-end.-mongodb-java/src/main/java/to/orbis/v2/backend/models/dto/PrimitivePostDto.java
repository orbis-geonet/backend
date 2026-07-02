package to.orbis.v2.backend.models.dto;

import lombok.Data;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.entity.RichLinkData;

import java.time.Instant;
import java.util.List;

@Data
public class PrimitivePostDto {
    PointDto coordinates;
    String postKey;
    PostType type;
    String title;
    String details;
    Instant plannedTime;
    RichLinkData richLinkData;
    List<String> mediaUrls;
}
