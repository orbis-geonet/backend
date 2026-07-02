package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.PlaceType;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Data
@Slf4j
public class PrimitivePlace {
    GeoJsonPoint coordinates;
    String name;
    String placeKey;
    PlaceType type;
    String address;
    String phone;
    List<WorkingHours> workingHours;;
    String website;
    String imageName;
}
