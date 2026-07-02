package to.orbis.v2.backend.models.entity;

import lombok.Data;
import org.mapstruct.Mapping;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.time.Instant;
import java.util.Set;

@Data
public class ExtendedUser {

    GeoJsonPoint coordinates;
    String userKey;
    Boolean superAdmin;
    String displayName;
    boolean deleted;
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
    boolean following;
    boolean pending;
    boolean blocked;

    Integer totalFollowing;
    Integer followedGroups;
    Integer followedPlaces;
    Integer totalFollowers;

    Integer groupAdminCount;
    Integer groupMemberCount;

    boolean accountPrivate;
    boolean reported;

    String shareLink;
    String fullShareLink;
    String slug;
}

