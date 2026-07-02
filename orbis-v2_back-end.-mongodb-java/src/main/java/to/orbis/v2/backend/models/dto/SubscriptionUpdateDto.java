package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.SubscriptionInterval;
import to.orbis.v2.backend.models.SubscriptionType;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class SubscriptionUpdateDto {
    @NotNull
    String subscriptionKey;

    String name;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    BigDecimal price;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    BigDecimal originalPrice;

    Currency currency;

    SubscriptionType type;

    SubscriptionInterval interval;

    @NotNull
    @Min(0)
    Integer period = 0;

    List<String> benefit;

    List<String> imagesName;
}
