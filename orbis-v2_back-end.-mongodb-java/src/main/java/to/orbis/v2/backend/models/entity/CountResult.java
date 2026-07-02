package to.orbis.v2.backend.models.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.Document;


@Data
@AllArgsConstructor
@FieldNameConstants(asEnum = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class CountResult {
    Integer result;
}
