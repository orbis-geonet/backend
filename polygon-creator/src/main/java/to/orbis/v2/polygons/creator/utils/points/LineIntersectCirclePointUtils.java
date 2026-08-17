package to.orbis.v2.polygons.creator.utils.points;

import lombok.experimental.UtilityClass;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.TangentPoints;

@UtilityClass
public class LineIntersectCirclePointUtils {

    public static boolean doesLineIntersectCircle(
            PointDto circleCenter, double radius, TangentPoints tangentPoints
    ) {
        // Convert points to Cartesian coordinates
        double[] p1 = toCartesian(tangentPoints.getFirstCirclePoint());
        double[] p2 = toCartesian(tangentPoints.getSecondCircePoint());
        double[] center = toCartesian(circleCenter);

        // Vector from point 1 to point 2
        double[] d = {p2[0] - p1[0], p2[1] - p1[1], p2[2] - p1[2]};

        // Vector from circle center to point 1
        double[] f = {p1[0] - center[0], p1[1] - center[1], p1[2] - center[2]};

        double a = dot(d, d);
        double b = 2 * dot(f, d);
        double c = dot(f, f) - radius * radius;

        double discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            // No intersection
            return false;
        } else {
            // Check if intersection points are within the line segment
            discriminant = Math.sqrt(discriminant);

            double t1 = (-b - discriminant) / (2 * a);
            double t2 = (-b + discriminant) / (2 * a);

            return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1);
        }
    }

    public static double[] toCartesian(PointDto pointDto) {
        double lat = Math.toRadians(pointDto.getLatitude());
        double lon = Math.toRadians(pointDto.getLongitude());
        double x = PointCommonUtils.EARTH_RADIUS * Math.cos(lat) * Math.cos(lon);
        double y = PointCommonUtils.EARTH_RADIUS * Math.cos(lat) * Math.sin(lon);
        double z = PointCommonUtils.EARTH_RADIUS * Math.sin(lat);
        return new double[]{x, y, z};
    }

    private static double dot(double[] v1, double[] v2) {
        return v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
    }
}
