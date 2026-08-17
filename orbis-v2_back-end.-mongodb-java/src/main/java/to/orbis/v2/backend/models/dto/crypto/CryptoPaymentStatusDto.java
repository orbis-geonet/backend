package to.orbis.v2.backend.models.dto.crypto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class CryptoPaymentStatusDto {
    String ref;
    String status;
    String coin;
    String txSignature;
    String solTxSignature;
    String distributionTxSignature;
    String solscanUrl;
    String solanaCluster;
    String amountOrbis;
    String amountSol;
    String amountLamports;
    String ownerCutOrbis;
    String cloneCutOrbis;
    String treasuryCutOrbis;
    String ownerCutOrbisRaw;
    String cloneCutOrbisRaw;
    String treasuryCutOrbisRaw;
    Integer attempts;
    String message;
}
