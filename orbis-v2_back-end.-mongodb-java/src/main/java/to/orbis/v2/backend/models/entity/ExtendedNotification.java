package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExtendedNotification extends Notification {
    PrimitiveUser fromUser;
    PrimitiveUser toUser;
    PrimitivePlace place;
    PrimitiveGroup group;
    PrimitivePost post;
    PrimitiveComment comment;
}
