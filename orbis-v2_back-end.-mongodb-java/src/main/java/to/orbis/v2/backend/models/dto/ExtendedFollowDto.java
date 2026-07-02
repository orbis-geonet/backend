package to.orbis.v2.backend.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import to.orbis.v2.backend.models.FollowType;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
@Slf4j
public class ExtendedFollowDto {

    @JsonProperty("type")
    public FollowType type() {
        if (user != null) {
            return FollowType.USER;
        } else if (group != null) {
            return FollowType.GROUP;
        } else if (place != null) {
            return FollowType.PLACE;
        } else {
            try {
                throw new RuntimeException("Incorrect follow");
            } catch (RuntimeException e) {
                log.error("Incorrect follow in database. Nothing actually followed", e);
            }
        }

        return null;
    }

    SimplifiedUserDto user;
    SimplifiedGroupDto group;
    PlaceDto place;
}
