package to.orbis.v2.backend.models.dto.stripe;

import lombok.Builder;
import lombok.Data;
import to.orbis.v2.backend.models.BusinessType;
import to.orbis.v2.backend.models.Country;
import to.orbis.v2.backend.models.StripeAccountStatus;

import java.util.List;

@Data
@Builder
public class StripeAccountInfoDto {
    StripeAccountStatus status;
    String userKey;
    BusinessType businessType;
    Country country;
    List<String> fieldError;
}
