package to.orbis.v2.backend.models.dto.partner;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDate;

@Data
@Builder
@FieldNameConstants(asEnum = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PartnerStatisticEarningInfoDto {
    String groupName;
    LocalDate createDate;
    String subscriptionName;
    Double amount;
    String currency;
}
