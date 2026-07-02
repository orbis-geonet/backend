package to.orbis.v2.backend.models.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorDto {
    int code;
    String message;
}
