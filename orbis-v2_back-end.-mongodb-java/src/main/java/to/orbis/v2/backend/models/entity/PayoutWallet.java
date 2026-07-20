package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@Document(collection = "payoutWallet")
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class PayoutWallet extends Entity {
    String userKey;
    String solanaPubkey;
    Instant timestamp;
    Instant createTimestamp;
}
