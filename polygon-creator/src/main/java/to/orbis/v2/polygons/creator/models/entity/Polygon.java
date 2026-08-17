package to.orbis.v2.polygons.creator.models.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.polygons.creator.models.dto.PointDto;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "polygons")
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PROTECTED)
public class Polygon {
    ObjectId id;
    String polygonKey;
    String groupKey;
    List<String> placeKeys;
    PointDto polygonCenter;
    List<PointDto> polygonPoints;
    LocalDateTime createdAt;
}