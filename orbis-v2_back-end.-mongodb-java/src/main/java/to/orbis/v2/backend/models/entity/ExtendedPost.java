package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExtendedPost extends Post {
    SimplifiedGroup group;
    SimplifiedPlace place;
    SimplifiedUser user;
    Double dist;
    Boolean seen;

    int commentsCount;
    int confirmedCount;
    boolean userLiked;
    boolean attending;

    Instant lastSeen;

    @Override
    public String toString() {
        return String.format("ExtendedPost[postKey=%s type=%s userKey=%s]", this.getPostKey(), this.getType(), this.getUserKey());
    }
}
