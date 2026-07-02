package to.orbis.v2.backend.models.requests.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import to.orbis.v2.backend.models.dto.PointDto;

import javax.validation.Valid;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDetailsRequest {
    @Valid
    PointDto coordinates;
    String displayName;
    String providerImageUrl;
    String unit;
    String imageName;
    String language;
    String dateOfBirth;
    String gender;
    Boolean accountPrivate;
    String partnerKey;
}
