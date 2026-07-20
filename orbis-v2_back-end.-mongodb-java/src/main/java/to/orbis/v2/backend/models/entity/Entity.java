package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.bson.types.ObjectId;

@Data
@FieldNameConstants(asEnum = true)
public abstract class Entity {
    ObjectId id;
    String networkActionId;
    String networkActionType;
    Integer networkPriority;
}
