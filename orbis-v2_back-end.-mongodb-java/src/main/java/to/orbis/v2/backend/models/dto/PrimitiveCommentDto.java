package to.orbis.v2.backend.models.dto;

import lombok.Data;

@Data
public class PrimitiveCommentDto {
    String commentKey;
    String postKey;
    String text;
}
