package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExtendedComment extends Comment {
    ExtendedPost post;
    List<ExtendedComment> replies;
    SimplifiedUser user;

    boolean userLiked;
}
