package to.orbis.v2.backend.utils;

import lombok.experimental.UtilityClass;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.Optional;

@UtilityClass
public class ControllerUtils {
    public static Optional<String> maybeAuthorized(Authentication auth) {
        return Optional.ofNullable(auth)
                .filter(a -> !(a instanceof AnonymousAuthenticationToken))
                .map(Principal::getName);
    }
}
