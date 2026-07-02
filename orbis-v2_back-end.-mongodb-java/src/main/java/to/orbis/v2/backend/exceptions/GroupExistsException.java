package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class GroupExistsException extends ResponseStatusException {
    public GroupExistsException() {
        super(HttpStatus.BAD_REQUEST, "Group with specified name already exists");
    }
}
