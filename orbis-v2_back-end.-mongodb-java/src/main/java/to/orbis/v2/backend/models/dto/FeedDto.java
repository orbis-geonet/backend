package to.orbis.v2.backend.models.dto;

import lombok.Data;

import java.util.List;

@Data
public class FeedDto {
    String nextPage;
    List<PostComponentDto> content;

    public static FeedDto empty() {
        return new FeedDto();
    }
}
