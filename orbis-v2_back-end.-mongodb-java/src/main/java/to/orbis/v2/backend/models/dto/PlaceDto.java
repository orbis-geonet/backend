package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.backend.models.PlaceType;
import to.orbis.v2.backend.models.entity.WorkingHours;

import java.time.Instant;
import java.util.List;

@Data
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class PlaceDto {
    PointDto coordinates;
    String name;
    String placeKey;
    PlaceType type;
    String userCreatedKey;
    String source;
    String address;
    String description;
    String categoryKey;
    String cityKey;
    String countryKey;
    String phone;
    List<WorkingHours> workingHours;;
    String website;
    Double averageRate;
    Double totalRate;
    Integer countRates;
    Double userRate;
    Instant lastCheckInTimestamp;
    Instant lastSizeChangeTimestamp;
    String dominantGroupKey;
    Instant creationServerTimestamp;
    Instant timestamp;
    Instant createTimestamp;
    String groupCreatedKey;
    String googlePlaceId;
    double size;
    double lastSize;
    String imageName;
    String shareLink;
    String fullShareLink;
    String slug;
    String checkInPolygonCoordinateKey;
}
