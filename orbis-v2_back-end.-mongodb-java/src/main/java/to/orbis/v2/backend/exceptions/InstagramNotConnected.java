package to.orbis.v2.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InstagramNotConnected extends ResponseStatusException {
    public InstagramNotConnected() {
        super(HttpStatus.BAD_REQUEST, "Please connect your instagram account");
    }
}
