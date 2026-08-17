package to.orbis.v2.polygons.creator.models.dto.polygon;

import lombok.Builder;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.TangentPoints;

import java.util.List;
import java.util.Set;

@Data
@Validated
@Builder
public class PolygonsDto {
    public List<List<PointDto>> circles;
    public Set<CirclePolygonDto> circlesDimension;
    public List<List<PointDto>> lines;
    public Set<TangentPoints> tangentPoints;
    public Set<PolygonLineDto> palindromeLines;
}
