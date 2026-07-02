package to.orbis.v2.backend.models.dto.partner;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants(asEnum = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PartnerAmountStatisticDto {
    String columnName;
    Double amount;

    public PartnerAmountStatisticDto(String columnName) {
        this.columnName = columnName;
        this.amount = 0D;
    }
}
