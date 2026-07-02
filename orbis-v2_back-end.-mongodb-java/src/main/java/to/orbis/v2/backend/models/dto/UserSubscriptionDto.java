package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.UserSubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@FieldNameConstants(asEnum = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class UserSubscriptionDto {
    String subscriptionKey;
    String name;
    String groupKey;
    String groupName;
    BigDecimal price;
    Currency currency;
    List<String> benefit;
    Instant startDate;
    Instant endDate;
    UserSubscriptionStatus status;
    String errorMessage;
    List<String> codes;
}
