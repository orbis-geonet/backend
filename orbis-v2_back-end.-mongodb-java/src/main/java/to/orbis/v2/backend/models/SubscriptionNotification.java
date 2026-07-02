package to.orbis.v2.backend.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubscriptionNotification {
    String userName;
    String subscriptionName;
    String groupName;
}
