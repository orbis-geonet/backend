package to.orbis.v2.backend.models.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import to.orbis.v2.backend.models.PolygonSchedulerCoordinateStatus;

@Data
@Validated
@Builder
@AllArgsConstructor
public class PolygonSchedulerCoordinateStatusDto {
    PolygonSchedulerCoordinateStatus status;
}