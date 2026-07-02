package to.orbis.v2.backend.models.dto;

import lombok.Data;
import to.orbis.v2.backend.models.EventType;

import java.time.Instant;

@Data
public class CheckInDto {
    Boolean duplicated;
    EventType eventType;
    String groupKey;
    String key;
    String placeKey;
    String userKey;
    Boolean valid;
    Instant validTimestamp;
    Instant invalidTimestamp;
    Instant timestamp;
}
