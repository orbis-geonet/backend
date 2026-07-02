package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class UserDto extends SimplifiedUserDto {
    Boolean superAdmin;
    String email;
    Instant timestamp;
    Instant createTimestamp;
    Instant activeServerTimestamp;
    String idToken;
    String refreshToken;
    String slug;
}
