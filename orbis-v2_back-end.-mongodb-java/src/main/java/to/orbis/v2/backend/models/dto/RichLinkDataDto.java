package to.orbis.v2.backend.models.dto;

import lombok.Data;

@Data
public class RichLinkDataDto {
    String canonicalUrl;
    String description;
    String imageUrl;
    String originalUrl;
    String title;
}
