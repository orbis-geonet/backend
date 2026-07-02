package to.orbis.v2.backend.models.dto;

import lombok.Data;

@Data
public class ChatUserDto {
    String userKey;
    String displayName;
    String imageName;
    String providerImageUrl;
    boolean blocked;
}
