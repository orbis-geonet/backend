package to.orbis.v2.backend.models.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@Data
@Validated
@Builder
public class PalindromeLineDto {
    public String placeKeyFirst;

    public String placeKeySecond;
    public TangentPoints tangentExternalLineFirst;
    public boolean isTouchedExternalLineFirst;
    public TangentPoints tangentExternalLineSecond;
    public boolean isTouchedExternalLineSecond;
    public TangentPoints tangentInternalLineFirst;
    public TangentPoints tangentInternalLineSecond;
    public PointDto tangentInternalLinesConnectionPoint;

    public TangentPoints tangentPointsCenters;
    public boolean isShow;
    public PointDto circleCenterPoint1;
    public double circleRadios1;
    public PointDto circleCenterPoint2;
    public double circleRadios2;

    public PalindromeLineWithPointsDto palindromeLineWithPointsList;
    public List<PointDto> polygon;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PalindromeLineDto lineDto = (PalindromeLineDto) o;
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
