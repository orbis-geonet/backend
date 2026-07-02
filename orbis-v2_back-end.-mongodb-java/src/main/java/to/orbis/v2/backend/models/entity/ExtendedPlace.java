package to.orbis.v2.backend.models.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = false)
public class ExtendedPlace extends Place {
    List<CompetingGroup> competingGroups;
    SimplifiedGroup dominantGroup;
    Double userRate;
    Double averageRate;
    Double dist;
    boolean following;
    boolean canEdit;
}
