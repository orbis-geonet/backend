package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UnknownUserException extends ResponseStatusException {
    public UnknownUserException() {
        super(HttpStatus.BAD_REQUEST, "User key must be provided or user must be authenticated");
    }
}
