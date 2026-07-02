package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "storiesSeen")
@Data
@Builder
@FieldNameConstants(asEnum = true)
public class StorySeen {
    String userKey;
    String postKey;
    Instant timestamp;
}
