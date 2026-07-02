package to.orbis.v2.backend.models.dto;

import lombok.Data;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.UserPictureType;

import java.time.Instant;
import java.util.List;

@Data
public class UserPictureDto {
    String pictureKey;
    String userKey;
    List<String> pictureUrl;
    UserPictureType type;
    PostType imageType;
    Instant timestamp;
}
