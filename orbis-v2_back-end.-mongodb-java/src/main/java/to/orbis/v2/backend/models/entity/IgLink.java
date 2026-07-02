package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.IgStatus;

import java.time.Instant;

@Data
@Document(collection = "igLink")
@FieldNameConstants(asEnum = true)
public class IgLink {

    @Builder
    public IgLink(ObjectId id, String userKey, Instant expirationTime, String token, long igUserId, String state, IgStatus status) {
        this.id = id;
        this.userKey = userKey;
        this.expirationTime = expirationTime;
        this.token = token;
        this.igUserId = igUserId;
        this.state = state;
        this.status = status;
    }

    ObjectId id;
    String userKey;
    Instant expirationTime;
    String token;
    long igUserId;
    String state;
    IgStatus status;

    @Transient
    String authLink;
}
