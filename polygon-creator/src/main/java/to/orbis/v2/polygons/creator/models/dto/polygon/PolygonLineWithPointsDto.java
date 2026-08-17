package to.orbis.v2.polygons.creator.models.dto.polygon;

import lombok.Builder;
import lombok.Data;
import to.orbis.v2.polygons.creator.models.dto.PointDto;

import java.util.List;

@Data
@Builder
public class PolygonLineWithPointsDto {
    public List<PointDto> lineFirst;
    public List<PointDto> circleFirst;
    public List<PointDto> lineSecond;
    public List<PointDto> circleSecond;
}
