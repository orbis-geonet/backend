package to.orbis.v2.backend.models.requests.comments;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CreateCommentRequest {

    String replyToKey;

    @NotBlank
    String text;
}
