package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.SubscriptionType;
import to.orbis.v2.backend.models.UserSubscriptionStatus;

import java.math.BigDecimal;
import java.util.List;

@Data
@FieldNameConstants(asEnum = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class UserPurchaseDto {
    String subscriptionKey;
    String name;
    String purchaseKey;
    String groupKey;
    String groupName;
    String userKey;
    String displayName;
    BigDecimal price;
    Currency currency;
    SubscriptionType type;
    UserSubscriptionStatus status;
    Integer number;
    List<String> codes;
}
