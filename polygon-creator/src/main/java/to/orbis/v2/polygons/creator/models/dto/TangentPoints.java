package to.orbis.v2.polygons.creator.models.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Builder
public class TangentPoints {
    public PointDto firstCirclePoint;
    public PointDto secondCircePoint;
}
