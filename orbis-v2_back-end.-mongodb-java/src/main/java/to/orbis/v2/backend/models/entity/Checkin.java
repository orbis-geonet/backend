package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@Document(collection = "checkins")
public class Checkin {
    ObjectId id;
    boolean duplicated;
    String eventType;
    String groupKey;
    String placeKey;
    String userKey;
    boolean valid;
    Instant validTimestamp;
    Instant invalidTimestamp;
    String polygonSchedulerCoordinateKey;
}
