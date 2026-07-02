package to.orbis.v2.backend.models.requests.places;

import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.springframework.validation.annotation.Validated;
import to.orbis.v2.backend.models.PlaceType;
import to.orbis.v2.backend.models.dto.PointDto;
import to.orbis.v2.backend.models.entity.WorkingHours;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Validated
public class UpdatePlaceRequest {
    @Valid
    PointDto coordinates;
    @Length(min = 1, message = "minimum length is 1 character")
    String name;
    PlaceType type;
    String source;
    String address;
    String description;
    String categoryKey;
    String cityKey;
    String countryKey;
    String phone;
    List<WorkingHours> workingHours;
    String website;
    String dominantGroupKey;
    String googlePlaceId;
    Boolean deleted;

    String imageName;
}
