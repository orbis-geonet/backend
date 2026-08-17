package to.orbis.v2.polygons.creator.utils.points;

import lombok.experimental.UtilityClass;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.TangentPoints;

import java.util.List;

@UtilityClass
public class LinesCalculationPointUtils {

    public static List<TangentPoints> calculateExternalTangents(
            PointDto firstPoint, double firstRadius,
            PointDto secondPoint, double secondRadius) {
        double lat1Rad = PointCommonUtils.toRadians(firstPoint.getLatitude());
        double lon1Rad = PointCommonUtils.toRadians(firstPoint.getLongitude());
        double lat2Rad = PointCommonUtils.toRadians(secondPoint.getLatitude());
        double lon2Rad = PointCommonUtils.toRadians(secondPoint.getLongitude());

        // Calculate the distance and angle between centers using the Haversine formula
        double deltaLon = lon2Rad - lon1Rad;
        double a = Math.sin((lat2Rad - lat1Rad) / 2) * Math.sin((lat2Rad - lat1Rad) / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double d = PointCommonUtils.EARTH_RADIUS * c;

        double angleBetweenCenters = Math.atan2(Math.sin(deltaLon) * Math.cos(lat2Rad),
                Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                        Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon));

        // Calculate the angle from the direction line to the tangents
        double angleToTangent = Math.acos((firstRadius - secondRadius) / d);

        // Correct the angles to find the tangent points for each circle
        double angle1 = angleBetweenCenters + angleToTangent;
        double angle2 = angleBetweenCenters - angleToTangent;

        // Find the points of tangency on both circles
        // First lone
        PointDto p1a = calculateTangentPoint(lat1Rad, lon1Rad, firstRadius, angle1);
        PointDto p2a = calculateTangentPoint(lat2Rad, lon2Rad, secondRadius, angle1);

        //Second line
        PointDto p1b = calculateTangentPoint(lat1Rad, lon1Rad, firstRadius, angle2);
        PointDto p2b = calculateTangentPoint(lat2Rad, lon2Rad, secondRadius, angle2);

        TangentPoints tangentPoints1 = TangentPoints.builder()
                .firstCirclePoint(p1a)
                .secondCircePoint(p2a)
                .build();
        TangentPoints tangentPoints2 = TangentPoints.builder()
                .firstCirclePoint(p1b)
                .secondCircePoint(p2b)
                .build();
        return List.of(tangentPoints1, tangentPoints2);
    }

    // Method to calculate the internal tangents
    public static List<TangentPoints> calculateInternalTangents(
            PointDto firstPoint, double firstRadius,
            PointDto secondPoint, double secondRadius) {
        // Convert latitude and longitude to radians
        double lat1Rad = PointCommonUtils.toRadians(firstPoint.getLatitude());
        double lon1Rad = PointCommonUtils.toRadians(firstPoint.getLongitude());
        double lat2Rad = PointCommonUtils.toRadians(secondPoint.getLatitude());
        double lon2Rad = PointCommonUtils.toRadians(secondPoint.getLongitude());

        // Calculate the distance between the centers of the circles
        double distCenters = PointCommonUtils.calculateDistanceBetweenPointsRad(lat1Rad, lon1Rad, lat2Rad, lon2Rad);

        // Calculate the radius of the auxiliary circle around c2
        double radius3 = firstRadius + secondRadius;

        // Calculate the angle from the x-axis to the line connecting the centers of the circles
        //let angle = Math.atan2(y2 - y1, x2 - x1);
        double angle = Math.atan2(lon2Rad - lon1Rad, lat2Rad - lat1Rad);

        // Calculate the angle of the tangent lines
        //let tangentAngle = Math.asin(r3 / distCenters) - Math.PI / 2;
        double tangentAngle = Math.asin(radius3 / distCenters) - Math.PI / 2;

        // Calculate the tangent points on circle c1
        PointDto t1 = calculateTangentPoint(lat1Rad, lon1Rad, firstRadius, angle + tangentAngle);
        PointDto t2 = calculateTangentPoint(lat1Rad, lon1Rad, firstRadius, angle - tangentAngle);

        // Calculate the tangent points on circle c2
        PointDto s1 = calculateTangentPoint(lat2Rad, lon2Rad, secondRadius, angle + tangentAngle + Math.PI);
        PointDto s2 = calculateTangentPoint(lat2Rad, lon2Rad, secondRadius, angle - tangentAngle + Math.PI);

        TangentPoints tangentPoints1 = TangentPoints.builder()
                .firstCirclePoint(t1)
                .secondCircePoint(s1)
                .build();
        TangentPoints tangentPoints2 = TangentPoints.builder()
                .firstCirclePoint(t2)
                .secondCircePoint(s2)
                .build();
        return List.of(tangentPoints1, tangentPoints2);
    }

    private static PointDto calculateTangentPoint(
            double latRad, double lonRad, double radius, double angle) {
        double newLatRad = Math.asin(Math.sin(latRad) * Math.cos(radius / PointCommonUtils.EARTH_RADIUS) +
                Math.cos(latRad) * Math.sin(radius / PointCommonUtils.EARTH_RADIUS) * Math.cos(angle));
        double newLonRad = lonRad + Math.atan2(Math.sin(angle) * Math.sin(radius / PointCommonUtils.EARTH_RADIUS) * Math.cos(latRad),
                Math.cos(radius / PointCommonUtils.EARTH_RADIUS) - Math.sin(latRad) * Math.sin(newLatRad));
        return PointDto.builder()
                .latitude(PointCommonUtils.toDegrees(newLatRad))
                .longitude(PointCommonUtils.toDegrees(newLonRad))
                .build();
    }

    public boolean shouldConnectByDistance(
            PointDto point1, double radius1, PointDto point2, double radius2
    ) {
        double distance = PointCommonUtils.calculateDistanceBetweenPoints(point1, point2);
        return distance - radius1 - radius2 < PointCommonUtils.MAX_DISTANCE_TO_CONNECT;
    }
}
