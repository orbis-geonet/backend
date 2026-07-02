package to.orbis.v2.backend.models.dto.stripe;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class FirstPaymentResult {
    String stripeId;
    String invoiceId;
    String paymentIntentId;
    String paymentClientSecret;
}
