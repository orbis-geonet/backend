package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.PartnerStatus;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "partner")
@FieldNameConstants(asEnum = true)
@Slf4j
public class Partner extends Entity{
    String partnerKey;
    String userKey;
    String partnerLink;
    PartnerStatus status;

    public Partner(String userKey) {
        val id = new ObjectId();
        this.setId(id);
        this.partnerKey = id.toHexString();
        this.status = PartnerStatus.CREATED;
        this.userKey = userKey;
    }
}
