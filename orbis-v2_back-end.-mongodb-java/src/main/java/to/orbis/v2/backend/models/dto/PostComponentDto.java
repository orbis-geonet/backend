package to.orbis.v2.backend.models.dto;

import lombok.Data;
import to.orbis.v2.backend.models.PostComponentType;

import java.time.Instant;
import java.util.List;

@Data
public class PostComponentDto {
    PostComponentType type;
    PostDto post;
    List<PostDto> slider;
}
