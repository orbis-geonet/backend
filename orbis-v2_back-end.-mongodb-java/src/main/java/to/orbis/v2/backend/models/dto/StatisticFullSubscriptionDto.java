package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@Data
@Builder
@FieldNameConstants(asEnum = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class StatisticFullSubscriptionDto {
    List<StatisticDto> resultList;
    Long totalNumber;
    Double totalAmount;
}
