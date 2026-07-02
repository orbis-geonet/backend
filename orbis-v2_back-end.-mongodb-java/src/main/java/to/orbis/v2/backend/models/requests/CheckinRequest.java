package to.orbis.v2.backend.models.requests;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;

@Data
@Validated
public class CheckinRequest {
    @NotEmpty
    String groupKey;
}
