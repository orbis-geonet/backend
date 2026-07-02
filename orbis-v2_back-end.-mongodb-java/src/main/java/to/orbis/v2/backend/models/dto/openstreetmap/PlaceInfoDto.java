package to.orbis.v2.backend.models.dto.openstreetmap;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class PlaceInfoDto {
    String city;
    String cityDistrict;
    String stateDistrict;
    String suburb;
    String municipality;
    String county;
    String country;
}
