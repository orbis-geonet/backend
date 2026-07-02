package to.orbis.v2.backend.models.dto;

import lombok.Data;
import to.orbis.v2.backend.models.NotificationType;

import java.time.Instant;

@Data
public class NotificationDto {
    String notificationKey;
    String title;
    String details;
    NotificationType type;
    boolean seen;
    Instant timestamp;
    PrimitiveUserDto fromUser;
    PrimitiveUserDto toUser;
    PrimitivePlaceDto place;
    PrimitiveGroupDto group;
    PrimitivePostDto post;
    PrimitiveCommentDto comment;
}
