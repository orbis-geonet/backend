package to.orbis.v2.backend.models.dto.partner;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.backend.models.PartnerStatus;
import to.orbis.v2.backend.models.dto.stripe.StripeAccountInfoDto;

@Data
@Builder
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PartnerFullDto {
    String partnerKey;
    PartnerStatus status;
    String partnerLink;
    String email;
    String displayName;
    StripeAccountInfoDto stripeInfo;
}
