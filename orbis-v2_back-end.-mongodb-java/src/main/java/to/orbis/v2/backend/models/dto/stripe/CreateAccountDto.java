package to.orbis.v2.backend.models.dto.stripe;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.backend.models.BusinessType;
import to.orbis.v2.backend.models.Country;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class CreateAccountDto {
    Country country;
    BusinessType businessType;
}
