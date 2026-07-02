package to.orbis.v2.backend.models.entity;

import lombok.Data;

@Data
public class RichLinkData {
    String canonicalUrl;
    String description;
    String imageUrl;
    String originalUrl;
    String title;
}
