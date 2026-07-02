package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.backend.models.IgStatus;

import java.time.Instant;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PRIVATE)
public class IgLinkDto {
    IgStatus status;
    String authLink;
    Instant expirationTime;
}
