package to.orbis.v2.backend.models.dto.stripe;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateAccountResponseDto {
    String stripeAccountKey;
    String setupAccountUrl;
}
