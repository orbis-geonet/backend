package to.orbis.v2.backend.models.entity;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.Language;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "users")
@FieldNameConstants(asEnum = true)
public class User extends Entity {

    @Builder
    public User(ObjectId id, GeoJsonPoint coordinates, String userKey,
                Boolean superAdmin, Boolean deleted, Set<String> fcmTokens, String email, Instant timestamp, Instant createTimestamp,
                Instant activeServerTimestamp, String providerImageUrl, String unit,
                String imageName, String language, String dateOfBirth, String gender, String displayName,
                Boolean accountPrivate, Boolean reported, String shareLink, String fullShareLink, Set<String> blockedBy, String reportedMessage, Boolean reportedSolved, Instant reportedTime,
                String customerStripeId, String partnerKey, String slug, String emptySlug) {
        this.setId(id);
        this.coordinates = coordinates;
        this.userKey = userKey;
        this.superAdmin = superAdmin != null && superAdmin;
        this.deleted = deleted != null && deleted;
        this.fcmTokens = fcmTokens;
        this.email = email;
        this.timestamp = timestamp;
        this.createTimestamp = createTimestamp;
        this.activeServerTimestamp = activeServerTimestamp;
        this.providerImageUrl = providerImageUrl;
        this.unit = unit;
        this.imageName = imageName;
        this.language = language;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.displayName = displayName;
        this.accountPrivate = accountPrivate != null && accountPrivate;
        this.reported = reported != null && reported;
        this.shareLink = shareLink;
        this.fullShareLink = fullShareLink;
        this.blockedBy = blockedBy != null ? blockedBy : new HashSet<>();
        this.reportedMessage = reportedMessage;
        this.reportedTime = reportedTime;
        this.reportedSolved = reportedSolved != null && reportedSolved;
        this.customerStripeId = customerStripeId;
        this.partnerKey = partnerKey;
        this.slug = slug;
        this.emptySlug = emptySlug;
    }

    GeoJsonPoint coordinates;
    String userKey;
    boolean superAdmin;
    boolean deleted;
    Set<String> fcmTokens;
    String email;
    Instant timestamp;
    Instant createTimestamp;
    Instant activeServerTimestamp;
    String providerImageUrl;
    String unit;
    String imageName;
    String language;
    String dateOfBirth;
    String gender;
    String displayName;
    boolean accountPrivate;
    boolean reported;
    String reportedMessage;
    Boolean reportedSolved;
    Instant reportedTime;
    String shareLink;
    String fullShareLink;
    Set<String> blockedBy;
    String customerStripeId;

    String partnerKey;

    @Transient
    String idToken = null;

    @Transient
    String refreshToken = null;

    String slug;

    String emptySlug;

    public User setTokens(Token token) {
        return setIdToken(token.getIdToken())
                .setRefreshToken(token.getRefreshToken());
    }
}

