package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "eventAttendees")
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class EventAttendee extends Entity {
    String postKey;
    String userKey;
}
