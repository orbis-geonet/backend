package to.orbis.v2.backend.models.dto;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import to.orbis.v2.backend.models.PlaceType;
import to.orbis.v2.backend.models.entity.WorkingHours;

import java.util.List;

@Data
@Slf4j
public class PrimitivePlaceDto {
    PointDto coordinates;
    String name;
    String placeKey;
    PlaceType type;
    String address;
    String phone;
    List<WorkingHours> workingHours;;
    String website;
    String imageName;
}
