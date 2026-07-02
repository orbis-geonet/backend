package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.SubscriptionInterval;
import to.orbis.v2.backend.models.SubscriptionType;

import java.math.BigDecimal;
import java.util.List;

@Data
@FieldNameConstants(asEnum = true)
@FieldDefaults(level = AccessLevel.PROTECTED)
public class SubscriptionDto {

    String subscriptionKey;
    String name;
    String description;
    String groupKey;
    BigDecimal price;
    BigDecimal originalPrice;
    Currency currency;
    SubscriptionType type;
    Integer period;
    SubscriptionInterval interval;
    List<String> benefit;
    Boolean isSubscriber;
    List<String> imagesName;

}
