package to.orbis.v2.backend.models.requests.users;

import lombok.Data;

@Data
public class ChangePasswordRequest {
    String email;
    String oldPassword;
    String newPassword;
}
