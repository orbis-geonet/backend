package to.orbis.v2.backend.models.requests.posts;

import lombok.Data;
import org.springframework.validation.annotation.Validated;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.dto.PointDto;
import to.orbis.v2.backend.models.dto.RichLinkDataDto;
import to.orbis.v2.backend.models.requests.validators.ValidEvent;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

@Validated
@Data
@ValidEvent
public class CreatePostRequest {

    @NotNull
    PointDto coordinates;
    @NotNull
    PostType type;
    boolean checkin;

    String title;
    String details;

    RichLinkDataDto richLinkData;
    List<String> mediaUrls;
    Instant plannedTime;
    Instant plannedEndTime;

    String groupKey;
    String placeKey;
    String address;
}
