package to.orbis.v2.backend.models.requests.users;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;

@Data
public class LoginRequest {
    @NotEmpty
    @Email
    String email;
    @NotEmpty
    String password;
}
