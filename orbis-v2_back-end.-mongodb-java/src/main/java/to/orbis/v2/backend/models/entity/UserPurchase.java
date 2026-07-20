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
@Document(collection = "userPurchase")
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class UserPurchase extends Entity {
    String userPurchaseKey;
    String userKey;
    String purchaseKey;
    String groupKey;
    String purchaseStripeId;
    String paymentRef;
    UserSubscriptionStatus status;
    String errorMessage;
    Integer number;
    List<String> codes;

    Instant lastPaymentTime;
    Instant timestamp;
    Instant createTimestamp;
}
