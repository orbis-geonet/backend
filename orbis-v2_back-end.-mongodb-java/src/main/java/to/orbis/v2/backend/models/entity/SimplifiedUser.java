package to.orbis.v2.backend.models.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

@Data
public class SimplifiedUser {

    GeoJsonPoint coordinates;
    String userKey;
    String displayName;
    String providerImageUrl;
    String unit;
    String imageName;
    String language;
    String dateOfBirth;
    String gender;
    boolean accountPrivate;
    String shareLink;
    String fullShareLink;
    boolean deleted;
    boolean seen;
    String slug;
}

