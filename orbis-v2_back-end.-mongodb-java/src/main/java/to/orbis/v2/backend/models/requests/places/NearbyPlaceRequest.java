package to.orbis.v2.backend.models.requests.places;

import lombok.Data;
import to.orbis.v2.backend.models.dto.PointDto;

@Data
public class NearbyPlaceRequest {
    PointDto location;
    int page = 0;
    int size = 100;
}
