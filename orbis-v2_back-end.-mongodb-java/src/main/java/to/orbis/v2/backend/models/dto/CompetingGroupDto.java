package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = false)
public class CompetingGroupDto extends SimplifiedGroupDto {
    Integer validCheckins;
    Double percentage;
}
