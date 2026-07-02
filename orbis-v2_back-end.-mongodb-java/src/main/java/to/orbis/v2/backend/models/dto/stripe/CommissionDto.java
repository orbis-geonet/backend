package to.orbis.v2.backend.models.dto.stripe;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.backend.models.Currency;

import java.util.List;

@Data
@Builder
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class CommissionDto {
    Double orbisCommission;
    Double stripeCommission;
    Double stripeAdditionFee;
    List<Currency> currencies;
}
