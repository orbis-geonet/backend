package to.orbis.v2.polygons.creator.models.dto.polygon;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.TangentPoints;

import java.util.List;
import java.util.Objects;

@Data
@Validated
@Builder
public class PolygonLineDto {
    public String placeKeyFirst;

    public String placeKeySecond;
    public TangentPoints tangentExternalLineFirst;
    public boolean isTouchedExternalLineFirst;
    public TangentPoints tangentExternalLineSecond;
    public boolean isTouchedExternalLineSecond;

    public TangentPoints tangentInternalLineFirst;
    public boolean isTouchedInternalLineFirst;

    public TangentPoints tangentInternalLineSecond;
    public boolean isTouchedInternalLineSecond;

    public PointDto tangentInternalLinesConnectionPoint;

    public TangentPoints tangentPointsCenters;
    public boolean isShow;
    public PointDto circleCenterPoint1;
    public double circleRadios1;
    public PointDto circleCenterPoint2;
    public double circleRadios2;

    public PolygonLineWithPointsDto palindromeLineWithPointsList;
    public List<PointDto> polygon;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolygonLineDto lineDto = (PolygonLineDto) o;
        return (circleRadios1 == lineDto.circleRadios1 && circleRadios2 == lineDto.circleRadios2 && circleCenterPoint1.equals(lineDto.circleCenterPoint1) && circleCenterPoint2.equals(lineDto.circleCenterPoint2)) ||
                (circleRadios1 == lineDto.circleRadios2 && circleRadios2 == lineDto.circleRadios1 && circleCenterPoint1.equals(lineDto.circleCenterPoint2) && circleCenterPoint2.equals(lineDto.circleCenterPoint1));
    }

    @Override
    public int hashCode() {
        int hash1 = Objects.hash(circleRadios1, circleRadios2, circleCenterPoint1, circleCenterPoint2);
        int hash2 = Objects.hash(circleRadios2, circleRadios1, circleCenterPoint2, circleCenterPoint1);
        return hash1 + hash2;
    }
}
