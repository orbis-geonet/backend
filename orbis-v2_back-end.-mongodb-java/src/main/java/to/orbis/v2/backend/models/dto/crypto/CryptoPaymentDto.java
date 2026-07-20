package to.orbis.v2.backend.models.dto.crypto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class CryptoPaymentDto {
    String ref;
    String amountOrbis;
    Double rate;
    String expiresAt;
    String unsignedTxBase64;
    Map<String, String> recipients;
}
