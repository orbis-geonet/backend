package to.orbis.v2.backend.models.dto;

import lombok.Data;

@Data
public class PrimitiveUserDto {
    String userKey;
    String displayName;
    String providerImageUrl;
    String imageName;
}

