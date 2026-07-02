package to.orbis.v2.backend.models.dto.partner;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants(asEnum = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PartnerStatisticDto {
    String columnName;
    Long number;

    public PartnerStatisticDto(String columnName) {
        this.columnName = columnName;
        this.number = 0L;
    }
}
