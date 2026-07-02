package to.orbis.v2.backend.models.dto;

import lombok.Data;

import java.util.List;

@Data
public class StoryDto {
    String storyKey;
    SimplifiedGroupDto group;
    List<PostDto> posts;
    Double dist;
}
