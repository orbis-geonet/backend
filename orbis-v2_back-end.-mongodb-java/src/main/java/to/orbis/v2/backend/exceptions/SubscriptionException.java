package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class SubscriptionException extends ResponseStatusException {
    public SubscriptionException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
