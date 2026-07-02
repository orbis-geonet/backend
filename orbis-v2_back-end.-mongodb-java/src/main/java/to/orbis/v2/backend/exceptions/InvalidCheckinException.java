package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidCheckinException extends ResponseStatusException {
    public InvalidCheckinException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
