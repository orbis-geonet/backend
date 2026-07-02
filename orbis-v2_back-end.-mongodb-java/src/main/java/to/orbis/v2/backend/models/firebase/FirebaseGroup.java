package to.orbis.v2.backend.models.firebase;

import lombok.Data;
import to.orbis.v2.backend.models.dto.PointDto;

import java.util.Map;

@Data
public class FirebaseGroup {

    String groupKey;
    String name;
    String imageName;
    int colorIndex;
    String solidColorHex;
    String strokeColorHex;
    Map<String, String> timestamp;
    Integer validCheckins;
    String placeKey;
    PointDto placeCoordinates;
}
