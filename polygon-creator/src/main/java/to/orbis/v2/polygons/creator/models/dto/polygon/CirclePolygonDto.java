package to.orbis.v2.polygons.creator.models.dto.polygon;

import lombok.Builder;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import to.orbis.v2.polygons.creator.models.dto.PointDto;

@Data
@Validated
@Builder
public class CirclePolygonDto {
    public PointDto center;
    public Double radios;
}
