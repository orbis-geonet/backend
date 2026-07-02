package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PlaceRateException extends ResponseStatusException {
    public PlaceRateException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
