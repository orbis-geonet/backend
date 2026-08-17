package to.orbis.v2.polygons.creator.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.SimplifiedGroupDto;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonLineDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PlaceForPalindromeCreationDto {
    String placeKey;
    double size;
    PointDto coordinates;

    Instant timestamp;

    String dominantGroupKey;
    SimplifiedGroupDto dominantGroup;

    Set<PolygonLineDto> tangentPoints = new HashSet<>();
    List<Set<String>> connectingPlacesByPair = new ArrayList<>();
}
