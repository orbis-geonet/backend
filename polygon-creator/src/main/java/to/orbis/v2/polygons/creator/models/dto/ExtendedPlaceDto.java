package to.orbis.v2.polygons.creator.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.polygons.creator.models.dto.polygon.CirclePolygonDto;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonLineDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class ExtendedPlaceDto extends PlaceDto {
    Set<PolygonLineDto> tangentPoints;
    List<Set<String>> connectingPlacesByPair = new ArrayList<>();
    CirclePolygonDto circleDto;
    SimplifiedGroupDto dominantGroup;
    List<PointDto> circle;
    List<String> palindromeGroupKeys;
}
