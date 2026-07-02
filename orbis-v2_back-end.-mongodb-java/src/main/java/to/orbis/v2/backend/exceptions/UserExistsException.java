package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UserExistsException extends ResponseStatusException {
    public UserExistsException() {
        super(HttpStatus.BAD_REQUEST, "Email already exists");
    }
}
