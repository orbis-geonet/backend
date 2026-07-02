package to.orbis.v2.backend.models.requests.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import to.orbis.v2.backend.models.dto.PointDto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSignupRequest {
    PointDto coordinates;
    @NotEmpty
    String displayName;
    @NotEmpty
    @Length(min = 8, message = "minimum length is 8 characters")
    String password;
    @NotEmpty
    @Email
    String email;
    @Pattern(regexp = "https?://.*", message = "must be a valid url if present")
    String providerImageUrl;
    String unit;
    String imageName;
    String language;
    String dateOfBirth;
    String gender;
    String partnerKey;
    boolean accountPrivate;
}
