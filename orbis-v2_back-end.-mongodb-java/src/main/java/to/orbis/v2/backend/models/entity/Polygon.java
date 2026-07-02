package to.orbis.v2.backend.models.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.dto.PointDto;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "polygons")
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PROTECTED)
public class Polygon extends Entity {
    String polygonKey;
    String groupKey;
    List<String> placeKeys;
    PointDto polygonCenter;
    List<PointDto> polygonPoints;
    LocalDateTime createdAt;
}