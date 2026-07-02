package to.orbis.v2.backend.models.requests.groups;

import lombok.Data;
import org.hibernate.validator.constraints.Length;
import to.orbis.v2.backend.models.dto.PointDto;

import javax.validation.Valid;

@Data
public class UpdateGroupRequest {
    String name;

    @Valid
    PointDto location;

    @Length(min = 15, message = "minimum length is 15 characters")
    String description;
    String imageName;
    int colorIndex;
    String solidColorHex;
    String strokeColorHex;
    String os;
}
