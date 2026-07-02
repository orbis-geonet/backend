package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PlacePalindromeDto extends ExtendedPlaceDto {
    String palindromeKey;
    PalindromeDto palindromeCoordinates;
    List<PointDto> polygonPoints;
    List<List<PointDto>> polygonPointsBeforeMerge;
    PointDto polygonCenter;
    List<ExtendedPlaceDto> places;
    List<PointDto> centerPoints;
    List<String> placeKeys;
}
