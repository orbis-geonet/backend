package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "comments")
@FieldNameConstants(asEnum = true)
public class Comment extends Entity {
    String commentKey;
    String postKey;
    String replyToKey;
    String userKey;
    String text;
    Instant timestamp;
    Instant createTimestamp;
    boolean deleted;

    Set<String> liked;

    public int getLikesCount() {
        if (getLiked() == null) return 0;
        return getLiked().size();
    }

    public boolean isReply() {
        return replyToKey != null && !replyToKey.isEmpty();
    }
}
