package to.orbis.v2.backend.models.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Builder
@Getter
public class StatisticRequestDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private String dataPattern;
    private String key;
}
