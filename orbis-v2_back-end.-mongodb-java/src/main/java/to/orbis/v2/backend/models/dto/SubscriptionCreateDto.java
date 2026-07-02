package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.SubscriptionInterval;
import to.orbis.v2.backend.models.SubscriptionType;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class SubscriptionCreateDto {
    @NotNull
    @NotEmpty
    String name;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    BigDecimal price;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    BigDecimal originalPrice;

    @NotNull
    Currency currency;

    @NotNull
    SubscriptionType type = SubscriptionType.UNLIMITED;

    @NotNull
    SubscriptionInterval interval = SubscriptionInterval.MONTH;

    @NotNull
    @Min(0)
    Integer period = 0;

    String description;

    List<String> benefit;

    List<String> imagesName;
}
