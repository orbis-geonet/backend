package to.orbis.v2.backend.models.firebase;

import lombok.Data;
import to.orbis.v2.backend.models.dto.PointDto;

import java.util.Map;

@Data
public class FirebasePlace {
    String placeKey;
    Long lastCheckInTimestamp;
    Long lastSizeChangeTimestamp;
    Double lastSize;
    PointDto coordinates;
}
