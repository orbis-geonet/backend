package to.orbis.v2.backend.models.ig;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
public class IgAccessToken {

    @JsonProperty("access_token")
    String accessToken;
    @JsonProperty("user_id")
    long userId;
    @JsonProperty("token_type")
    String tokenType;
    @JsonProperty("expires_in")
    Integer expiresIn;
}
