package to.orbis.v2.polygons.creator.models.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.polygons.creator.models.dto.ExtendedPlaceDto;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.SimplifiedGroupDto;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonsDto;

import java.util.List;

@Data
@Builder
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PlacePalindromeCreationDto {

    String dominantGroupKey;
    SimplifiedGroupDto dominantGroup;

    PolygonsDto palindromeInformationForCreation;
    List<PointDto> polygonPoints;
    List<List<PointDto>> polygonPointsBeforeMerge;
    PointDto polygonCenter;
    List<String> placeKeys;
}
