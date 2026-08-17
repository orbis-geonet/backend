package to.orbis.v2.polygons.creator.utils.points;

import lombok.experimental.UtilityClass;
import to.orbis.v2.polygons.creator.models.dto.PointDto;

@UtilityClass
public class PointCommonUtils {
    // Earth's radius in meters
    public static final double EARTH_RADIUS = 6371000;
    public static final int CIRCLE_NUM_POINTS = 100;
    public static final int LINE_NUM_POINTS = 20;
    public static final double MIN_RADIOS_TO_SHOW = 50;
    public static final double MAX_DISTANCE_TO_CONNECT = 1000;
    public static final double MIN_DISTANCE_BETWEEN_POINTS = 150;

    // Method to convert degrees to radians
    public static double toRadians(double degrees) {
        return degrees * Math.PI / 180;
    }

    // Method to convert radians to degrees
    public static double toDegrees(double radians) {
        return radians * 180 / Math.PI;
    }

    public double calculateDistanceBetweenPoints(
            PointDto point1, PointDto point2
    ) {
        double lat1Rad = toRadians(point1.getLatitude());
        double lon1Rad = toRadians(point1.getLongitude());
        double lat2Rad = toRadians(point2.getLatitude());
        double lon2Rad = toRadians(point2.getLongitude());

        return calculateDistanceBetweenPointsRad(lat1Rad, lon1Rad, lat2Rad, lon2Rad);
    }

    public double calculateDistanceBetweenPointsWithoutEarth(
            PointDto point1, PointDto point2
    ) {
        double lat1Rad = toRadians(point1.getLatitude());
        double lon1Rad = toRadians(point1.getLongitude());
        double lat2Rad = toRadians(point2.getLatitude());
        double lon2Rad = toRadians(point2.getLongitude());

        return calculateDistanceBetweenPointsRadWithoutEarth(lat1Rad, lon1Rad, lat2Rad, lon2Rad);
    }

    public double calculateDistanceBetweenPointsRad(
            double lat1Rad, double lon1Rad, double lat2Rad, double lon2Rad) {
        double c = calculateDistanceBetweenPointsRadWithoutEarth(lat1Rad, lon1Rad, lat2Rad, lon2Rad);
        return EARTH_RADIUS * c;
    }

    public double calculateDistanceBetweenPointsRadWithoutEarth(
            double lat1Rad, double lon1Rad, double lat2Rad, double lon2Rad) {
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
