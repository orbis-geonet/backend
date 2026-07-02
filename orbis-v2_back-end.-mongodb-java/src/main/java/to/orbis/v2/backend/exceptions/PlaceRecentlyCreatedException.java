package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PlaceRecentlyCreatedException extends ResponseStatusException {
    public PlaceRecentlyCreatedException() {
        super(HttpStatus.CONFLICT, "User recently created another place");
    }
}
