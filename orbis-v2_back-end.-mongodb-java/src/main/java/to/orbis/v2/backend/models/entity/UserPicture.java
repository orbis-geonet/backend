package to.orbis.v2.backend.models.entity;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.UserPictureType;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "userPicture")
public class UserPicture {

    ObjectId id;
    String pictureKey;
    String userKey;
    List<String> pictureUrl;
    UserPictureType type;
    PostType imageType;
    Instant timestamp;
    String igMediaId;
    Instant loadTimestamp;
}
