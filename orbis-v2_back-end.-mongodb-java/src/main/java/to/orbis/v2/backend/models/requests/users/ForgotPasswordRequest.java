package to.orbis.v2.backend.models.requests.users;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    String email;
    String userIp;
}
