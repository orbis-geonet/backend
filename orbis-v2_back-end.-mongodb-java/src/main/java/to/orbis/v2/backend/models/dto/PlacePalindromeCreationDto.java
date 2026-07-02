package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PlacePalindromeCreationDto {
    List<ExtendedPlaceDto> places; //It need for FE

    String dominantGroupKey;
    SimplifiedGroupDto dominantGroup;

    PalindromeDto palindromeInformationForCreation;
    List<PointDto> polygonPoints;
    List<List<PointDto>> polygonPointsBeforeMerge;
    PointDto polygonCenter;
    List<String> placeKeys;
}
