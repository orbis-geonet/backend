package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class ExtendedPlaceDto extends PlaceDto {
    List<CompetingGroupDto> competingGroups;
    SimplifiedGroupDto dominantGroup;
    Double dist;
    boolean following;
    boolean canEdit;
}
