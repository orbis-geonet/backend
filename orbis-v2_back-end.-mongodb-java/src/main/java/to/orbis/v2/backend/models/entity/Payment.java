package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Slf4j
@Builder
@Document(collection = "payment")
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class Payment extends Entity {
    String paymentId;
    String userSubscriptionKey;

    BigDecimal amount;
    BigDecimal amountAfterStripCommission;
    Currency currency;
    PaymentType paymentType;
    PaymentStatus status;

    String paymentIntentStripeId;
    String chargeStripeId;
    String paymentMethodStripeId;
    String customerStripeId;
    String invoiceStripeId;
    String stripeOrderId;

    String paymentRef;
    String txSignature;

    Instant timestamp;
    Instant createTimestamp;
}
