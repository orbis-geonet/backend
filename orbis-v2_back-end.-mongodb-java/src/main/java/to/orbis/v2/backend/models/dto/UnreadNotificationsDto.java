package to.orbis.v2.backend.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UnreadNotificationsDto {
    long notifications;
    long pendingRequests;
}
