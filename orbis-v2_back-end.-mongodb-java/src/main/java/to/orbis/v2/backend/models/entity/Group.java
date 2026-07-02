package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "groups")
@FieldNameConstants(asEnum = true)
public class Group extends Entity {

    public Group(ObjectId id, String groupKey, String name, GeoJsonPoint location, String description,
                 String imageName, int colorIndex, String solidColorHex, String strokeColorHex, Instant timestamp, Instant createTimestamp,
                 Boolean deleted, String os, Set<String> admins, Set<String> followers, Set<String> members, Set<String> banned,
                 Set<String> storiesHidden, String shareLink, String fullShareLink, Boolean reported, String reportedMessage, Boolean reportedSolved, Instant reportedTime,
                 Boolean isSubscriptionActivate, Set<String> subscriptions, String mainAdmin, Set<String> blockedBy,
                 String slug) {
        this.setId(id);
        this.groupKey = groupKey;
        this.name = name;
        this.location = location;
        this.description = description;
        this.imageName = imageName;
        this.colorIndex = colorIndex;
        this.solidColorHex = solidColorHex;
        this.strokeColorHex = strokeColorHex;
        this.timestamp = timestamp;
        this.createTimestamp = createTimestamp;
        this.deleted = deleted != null && deleted;
        this.os = os;
        this.admins = admins == null ? new HashSet<>() : admins;
        this.members = members == null ? new HashSet<>() : members;
        this.followers = followers == null ? new HashSet<>() : followers;
        this.banned = banned == null ? new HashSet<>() : banned;
        this.storiesHidden = storiesHidden == null ? new HashSet<>() : banned;
        this.shareLink = shareLink;
        this.fullShareLink = fullShareLink;
        this.reported = reported != null && reported;
        this.reportedMessage = reportedMessage;
        this.reportedTime = reportedTime;
        this.reportedSolved = reportedSolved != null && reportedSolved;
        this.isSubscriptionActivate = isSubscriptionActivate != null && isSubscriptionActivate;
        this.subscriptions = subscriptions;
        this.mainAdmin = mainAdmin;
        this.blockedBy = blockedBy;
        this.slug = slug;
    }

    String groupKey;
    String name;
    GeoJsonPoint location;
    String description;
    String imageName;
    int colorIndex;
    String solidColorHex;
    String strokeColorHex;
    Instant timestamp;
    Instant createTimestamp;
    boolean deleted;
    boolean reported;
    String reportedMessage;
    Boolean reportedSolved;
    Instant reportedTime;
    String os;
    Set<String> admins;
    Set<String> followers;
    Set<String> members;
    Set<String> banned;
    Set<String> storiesHidden;
    Set<String> blockedBy;

    String shareLink;
    String fullShareLink;

    Boolean isSubscriptionActivate;
    Set<String> subscriptions;
    String mainAdmin;
    String slug;
    String emptySlug;
}
