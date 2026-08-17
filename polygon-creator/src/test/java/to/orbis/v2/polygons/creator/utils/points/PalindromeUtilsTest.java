package to.orbis.v2.polygons.creator.utils.points;

import org.junit.jupiter.api.Test;
import to.orbis.v2.polygons.creator.models.dto.PointDto;

import java.util.ArrayList;
import java.util.List;

class PalindromeUtilsTest {

    @Test
    void findConvexHullTest() {
        List<PointDto> points = new ArrayList<>();
        points.add(new PointDto(2.0, 2.0));
        points.add(new PointDto(2.0, -2.0));
        points.add(new PointDto(-2.0, -2.0));
        points.add(new PointDto(-1.0, 2.0));

        points.add(new PointDto(1.0, 0.0));

        points.add(new PointDto(0.0, -2.0));
        points.add(new PointDto(-1.0, 0.0));
        points.add(new PointDto(0.0, 2.0));

        points.add(new PointDto(0.0, 0.0));  // This is an internal point

        List<PointDto> convexHull = PalindromeUtils.findBorderPoints(points);

        System.out.println("Convex Hull:");
        for (PointDto p : convexHull) {
            System.out.println(p);
        }
    }
}