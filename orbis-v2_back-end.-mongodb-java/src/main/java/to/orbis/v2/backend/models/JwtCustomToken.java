package to.orbis.v2.backend.models;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import to.orbis.v2.backend.models.entity.User;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class JwtCustomToken extends JwtAuthenticationToken {
    User user;

    public JwtCustomToken(Jwt jwt, User user) {
        super(jwt);
        this.user = user;
    }
}
