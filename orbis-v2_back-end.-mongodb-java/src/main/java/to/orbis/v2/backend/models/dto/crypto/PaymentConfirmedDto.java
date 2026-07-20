package to.orbis.v2.backend.models.dto.crypto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PaymentConfirmedDto {
    String ref;
    String txSignature;
}
