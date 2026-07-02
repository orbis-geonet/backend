package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.NotificationType;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "notifications")
@FieldNameConstants(asEnum = true)
public class Notification extends Entity {
    String notificationKey;
    String forUserKey;
    String fromUserKey;
    String placeKey;
    String groupKey;
    String postKey;
    String commentKey;
    String title;
    String details;
    NotificationType type;
    boolean seen;
    Instant timestamp;
}
