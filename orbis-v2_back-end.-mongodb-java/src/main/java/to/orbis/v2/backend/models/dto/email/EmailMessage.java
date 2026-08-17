package to.orbis.v2.backend.models.dto.email;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@Data
@Builder
@FieldNameConstants(asEnum = true)
public class EmailMessage {
    private String to;
    private EmailType emailType;
    private String language;

    private String userName;
    private String userEmail;
    private String purchaseName;
    private Integer quantity;
    private String contactName;
    private String contactEmail;
    private String nameOfGroup;
    private String codes;
    private String txSignature;
    private String solscanUrl;
    private String solanaCluster;
    private String ownerCutOrbis;
    private String cloneCutOrbis;
    private String treasuryCutOrbis;
}
