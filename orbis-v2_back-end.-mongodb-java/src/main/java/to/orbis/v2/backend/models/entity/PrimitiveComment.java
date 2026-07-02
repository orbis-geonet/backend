package to.orbis.v2.backend.models.entity;

import lombok.Data;

@Data
public class PrimitiveComment {
    String commentKey;
    String postKey;
    String text;
}
