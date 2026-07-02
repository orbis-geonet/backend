package to.orbis.v2.backend.models.dto.email;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CreateEmailTemplateRequest {
    private String body;
    private String subject;
    private String templateName;
}
