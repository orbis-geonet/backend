package to.orbis.v2.backend.models.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ShareLink {
    private String shortLink;
    private String fullLink;
}
