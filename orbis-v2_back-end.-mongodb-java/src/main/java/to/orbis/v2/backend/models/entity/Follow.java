package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "follows")
@FieldNameConstants(asEnum = true)
@Slf4j
public class Follow extends Entity {
    String followerKey;
    String placeKey;
    String groupKey;
    String userKey;
    boolean accepted;
    boolean seen;

    public static Follow newUserFollow(User followerUser, User userToFollow) {
        val follow = new Follow()
                .setFollowerKey(followerUser.getUserKey())
                .setUserKey(userToFollow.getUserKey())
                .setSeen(true);
        if (!userToFollow.isAccountPrivate()) {
            return follow.setAccepted(true);
        }
        return follow.setSeen(false);
    }

    public static Follow newPlaceFollow(User followerUser, Place placeToFollow) {
        return new Follow()
                .setFollowerKey(followerUser.getUserKey())
                .setPlaceKey(placeToFollow.getPlaceKey())
                .setAccepted(true)
                .setSeen(true);
    }

    public static Follow newGroupFollow(User followerUser, Group groupToFollow) {
        return new Follow()
                .setFollowerKey(followerUser.getUserKey())
                .setGroupKey(groupToFollow.getGroupKey())
                .setAccepted(true)
                .setSeen(true);
    }
}
