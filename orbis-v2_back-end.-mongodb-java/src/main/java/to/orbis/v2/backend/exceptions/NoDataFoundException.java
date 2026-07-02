package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class NoDataFoundException extends ResponseStatusException {
    public NoDataFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
