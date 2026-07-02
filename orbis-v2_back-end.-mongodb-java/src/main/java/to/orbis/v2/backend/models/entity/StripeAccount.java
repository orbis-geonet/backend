package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.BusinessType;
import to.orbis.v2.backend.models.Country;
import to.orbis.v2.backend.models.StripeAccountStatus;

import java.time.Instant;
import java.util.List;

@Data
@Slf4j
@Builder
@Document(collection = "stripeAccount")
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class StripeAccount extends Entity {
    String stripeAccountKey;
    String stripeId;
    StripeAccountStatus status;
    String userKey;
    BusinessType businessType;
    Country country;
    List<String> fieldError;

    Instant timestamp;
    Instant createTimestamp;
    Boolean deleted;
}
