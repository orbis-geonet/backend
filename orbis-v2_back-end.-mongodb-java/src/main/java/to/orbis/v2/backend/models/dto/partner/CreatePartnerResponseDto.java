package to.orbis.v2.backend.models.dto.partner;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.backend.models.PartnerStatus;

@Data
@Builder
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class CreatePartnerResponseDto {
    String partnerKey;
    String stripeAccountKey;
    String setupAccountUrl;
    PartnerStatus status;
}
