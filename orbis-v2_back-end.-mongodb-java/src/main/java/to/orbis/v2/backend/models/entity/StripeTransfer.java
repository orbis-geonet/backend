package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.StripeTransferType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Slf4j
@Builder
@Document(collection = "stripeTransfer")
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class StripeTransfer extends Entity {
    String transferStripeKey;
    String paymentId;
    String userKey;
    String stripeAccountId;
    StripeTransferType type;

    BigDecimal amount;
    Currency currency;

    String stripeOrderId;
    String transferStripId;

    Instant timestamp;
    Instant createTimestamp;
}
