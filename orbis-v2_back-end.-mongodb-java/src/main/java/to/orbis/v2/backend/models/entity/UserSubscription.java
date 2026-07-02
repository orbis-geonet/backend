package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.SubscriptionType;
import to.orbis.v2.backend.models.UserSubscriptionStatus;

import java.time.Instant;
import java.util.List;

@Data
@Slf4j
@Builder
@Document(collection = "userSubscription")
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class UserSubscription extends Entity {
    String userSubscriptionKey;
    String userKey;
    String subscriptionKey;
    String groupKey;
    String subscriptionStripeId;
    UserSubscriptionStatus status;
    String errorMessage;
    Instant startDate;
    Instant endDate;
    List<String> codes;

    Instant lastPaymentTime;
    Instant timestamp;
    Instant createTimestamp;
    SubscriptionType type;
}
