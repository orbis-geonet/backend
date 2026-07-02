package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@Data
@FieldNameConstants(asEnum = true)
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = false)
public class SimplifiedUserDto {

    PointDto coordinates;
    String userKey;
    String providerImageUrl;
    String unit;
    String imageName;
    String language;
    String dateOfBirth;
    String displayName;
    String gender;
    boolean accountPrivate;
    boolean deleted;
    String shareLink;
    String fullShareLink;
    boolean seen;
    List<String> codes;
    String slug;
}

