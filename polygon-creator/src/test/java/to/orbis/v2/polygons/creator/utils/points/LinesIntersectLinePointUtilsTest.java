package to.orbis.v2.polygons.creator.utils.points;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.TangentPoints;

class LinesIntersectLinePointUtilsTest {

    @Test
    void doesLinesIntersectLine() {
    }

    @Test
    void doesLineIntersectLine_returnTrue_lineIntersect() {
        //-34.843087, -56.196632
        //-34.885976, -56.143074
        TangentPoints tangentPoints1 = TangentPoints.builder()
                .firstCirclePoint(PointDto.builder()
                        .latitude(-34.843087)
                        .longitude(-56.196632)
                        .build())
                .secondCircePoint(PointDto.builder()
                        .latitude(-34.885976)
                        .longitude(-56.143074)
                        .build())
                .build();

        //-34.874358, -56.190710
        //-34.852262, -56.154382
        TangentPoints tangentPoints2 = TangentPoints.builder()
                .firstCirclePoint(PointDto.builder()
                        .latitude(-34.874358)
                        .longitude(-56.190710)
                        .build())
                .secondCircePoint(PointDto.builder()
                        .latitude(-34.852262)
                        .longitude(-56.154382)
                        .build())
                .build();

        boolean intersection = LinesIntersectLinePointUtils.doesLineIntersectLine(
                tangentPoints1, tangentPoints2);

        //should return true as Lines intersect
        Assertions.assertTrue(intersection);
    }

    @Test
    void doesLineIntersectLine_returnFalse_lineNotIntersect() {
        //-34.905194, -56.149082
        //-34.892805, -56.122303
        TangentPoints tangentPoints1 = TangentPoints.builder()
                .firstCirclePoint(PointDto.builder()
                        .latitude(-34.905194)
                        .longitude(-56.149082)
                        .build())
                .secondCircePoint(PointDto.builder()
                        .latitude(-34.892805)
                        .longitude(-56.122303)
                        .build())
                .build();

        //-34.864781, -56.189594
        //-34.840410, -56.164188
        TangentPoints tangentPoints2 = TangentPoints.builder()
                .firstCirclePoint(PointDto.builder()
                        .latitude(-34.864781)
                        .longitude(-56.189594)
                        .build())
                .secondCircePoint(PointDto.builder()
                        .latitude(-34.840410)
                        .longitude(-56.164188)
                        .build())
                .build();

        boolean intersection = LinesIntersectLinePointUtils.doesLineIntersectLine(
                tangentPoints1, tangentPoints2);

        Assertions.assertFalse(intersection);
    }
}