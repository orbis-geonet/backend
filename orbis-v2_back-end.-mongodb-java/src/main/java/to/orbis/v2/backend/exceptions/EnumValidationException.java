package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EnumValidationException extends ResponseStatusException {
    public EnumValidationException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}
