package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants(asEnum = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class StatisticDto {
    String columnName;
    Long number;
    Double amount;

    public StatisticDto(String columnName) {
        this.columnName = columnName;
        this.number = 0L;
        this.amount = 0.0;
    }
}
