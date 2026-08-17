package to.orbis.v2.polygons.creator.models.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = false)
public class ExtendedPlace extends Place {
    SimplifiedGroup dominantGroup;
}
