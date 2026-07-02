package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.SubscriptionInterval;
import to.orbis.v2.backend.models.SubscriptionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Slf4j
@Document(collection = "subscription")
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class Subscription extends Entity {

    String subscriptionKey;
    String stripeProductId;
    String stripePriceId;
    String name;
    String description;
    String groupKey;
    BigDecimal price;
    BigDecimal originalPrice;
    Currency currency;
    SubscriptionType type;
    SubscriptionInterval interval;
    Integer period;
    List<String> benefit;
    Instant timestamp;
    Instant createTimestamp;
    Boolean deleted;
    String createdUserKey;
    List<String> imagesName;
}
