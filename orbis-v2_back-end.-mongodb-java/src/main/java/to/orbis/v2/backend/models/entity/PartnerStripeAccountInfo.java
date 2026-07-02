package to.orbis.v2.backend.models.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants(asEnum = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PartnerStripeAccountInfo {
    String partnerKey;
    String userKey;
    String stripeId;
}
