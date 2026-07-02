package to.orbis.v2.backend.models.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class CompetingGroup extends SimplifiedGroup {

    Integer validCheckins;
    Double percentage;

}
