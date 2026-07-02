package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Document(collection = "placeRates")
@FieldNameConstants(asEnum = true)
public class PlaceRate extends Entity {
    String placeRateKey;
    String placeKey;
    String userKey;
    Double userRate;
}
