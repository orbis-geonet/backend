package to.orbis.v2.backend.models.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CommentDto {
    String commentKey;
    PostDto post;
    String replyToKey;
    List<CommentDto> replies;
    SimplifiedUserDto user;
    String text;
    Instant timestamp;
    Instant createTimestamp;
    boolean deleted;
    int likesCount;

    boolean userLiked;
}
