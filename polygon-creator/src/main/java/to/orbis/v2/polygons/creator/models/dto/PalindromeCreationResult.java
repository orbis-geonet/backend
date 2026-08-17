package to.orbis.v2.polygons.creator.models.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonsDto;

import java.util.List;

@Data
@Builder
public class PalindromeCreationResult {

    List<PlacePalindromeCreationDto> palindromes;
    int numberOfPlaces;
    int numberOfPolygons;
}
