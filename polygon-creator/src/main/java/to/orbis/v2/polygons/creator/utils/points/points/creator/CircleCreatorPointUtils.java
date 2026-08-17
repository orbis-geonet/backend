package to.orbis.v2.polygons.creator.utils.points.points.creator;

import lombok.experimental.UtilityClass;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonLineDto;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.utils.points.PointCommonUtils;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class CircleCreatorPointUtils {

    public static List<PointDto> creteFullCircle(
            PointDto center, double radius) {
        List<PointDto> circlePoints = new ArrayList<>();
        double latRad = PointCommonUtils.toRadians(center.getLatitude());
        double lngRad = PointCommonUtils.toRadians(center.getLongitude());

        double angularDistance = radius / PointCommonUtils.EARTH_RADIUS;

        for (int i = 0; i < PointCommonUtils.CIRCLE_NUM_POINTS; i++) {
            double bearing = 2 * Math.PI * i / PointCommonUtils.CIRCLE_NUM_POINTS;
            double pointLatRad = Math.asin(Math.sin(latRad) * Math.cos(angularDistance) +
                    Math.cos(latRad) * Math.sin(angularDistance) * Math.cos(bearing));
            double pointLngRad = lngRad + Math.atan2(Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(latRad),
                    Math.cos(angularDistance) - Math.sin(latRad) * Math.sin(pointLatRad));
            double pointLat = PointCommonUtils.toDegrees(pointLatRad);
            double pointLng = PointCommonUtils.toDegrees(pointLngRad);
            PointDto pointDto = PointDto.builder()
                    .latitude(pointLat)
                    .longitude(pointLng)
                    .build();
            circlePoints.add(pointDto);
        }

        return circlePoints;
    }

    public static List<PointDto> createFirstCircleLines(PolygonLineDto line) {
        PointDto startPoint = line.getTangentExternalLineSecond().firstCirclePoint;
        if (line.isTouchedExternalLineSecond) {
            startPoint = line.getTangentInternalLineSecond().firstCirclePoint;
        }

        PointDto endPoint = line.getTangentExternalLineFirst().firstCirclePoint;
        if (line.isTouchedExternalLineFirst) {
            endPoint = line.getTangentInternalLineFirst().firstCirclePoint;
        }

        return CircleCreatorPointUtils.cretePartCircle(
                line.circleCenterPoint1, line.circleRadios1,
                startPoint,
                endPoint,
                false
        );
    }

    public static List<PointDto> createSecondCircleLines(PolygonLineDto line) {
        PointDto startPoint = line.getTangentExternalLineFirst().secondCircePoint;
        if (line.isTouchedExternalLineFirst) {
            startPoint = line.getTangentInternalLineSecond().secondCircePoint;
        }
        PointDto endPoint = line.getTangentExternalLineSecond().secondCircePoint;
        if (line.isTouchedExternalLineSecond) {
            endPoint = line.getTangentInternalLineFirst().secondCircePoint;
        }

        return CircleCreatorPointUtils.cretePartCircle(
                line.circleCenterPoint2, line.circleRadios2,
                startPoint,
                endPoint,
                false
        );
    }

    public static List<PointDto> cretePartCircle(
            PointDto center, double radius, PointDto startPoint, PointDto endPoint, boolean isClockwise) {
        List<PointDto> circlePoints = new ArrayList<>();
        double centerLatRad = PointCommonUtils.toRadians(center.getLatitude());
        double centerLngRad = PointCommonUtils.toRadians(center.getLongitude());

        double startLatRad = PointCommonUtils.toRadians(startPoint.getLatitude());
        double startLngRad = PointCommonUtils.toRadians(startPoint.getLongitude());

        double endLatRad = PointCommonUtils.toRadians(endPoint.getLatitude());
        double endLngRad = PointCommonUtils.toRadians(endPoint.getLongitude());

        double angularDistance = radius / PointCommonUtils.EARTH_RADIUS;

        double startBearing = calculateBearing(centerLatRad, centerLngRad, startLatRad, startLngRad);
        double endBearing = calculateBearing(centerLatRad, centerLngRad, endLatRad, endLngRad);

        double angleStep = (2 * Math.PI) / PointCommonUtils.CIRCLE_NUM_POINTS;
        if (!isClockwise) {
            angleStep = -angleStep;
        }

        if (isClockwise && startBearing > endBearing) {
            endBearing += 2 * Math.PI;
        } else if (!isClockwise && startBearing < endBearing) {
            startBearing += 2 * Math.PI;
        }

        for (double bearing = startBearing;
             isClockwise ? bearing <= endBearing : bearing >= endBearing;
             bearing += angleStep) {
            double pointLatRad = Math.asin(Math.sin(centerLatRad) * Math.cos(angularDistance) +
                    Math.cos(centerLatRad) * Math.sin(angularDistance) * Math.cos(bearing));
            double pointLngRad = centerLngRad + Math.atan2(Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(centerLatRad),
                    Math.cos(angularDistance) - Math.sin(centerLatRad) * Math.sin(pointLatRad));
            double pointLat = PointCommonUtils.toDegrees(pointLatRad);
            double pointLng = PointCommonUtils.toDegrees(pointLngRad);
            PointDto pointDto = PointDto.builder()
                    .latitude(pointLat)
                    .longitude(pointLng)
                    .build();
            circlePoints.add(pointDto);
        }

        return circlePoints;
    }

    private static double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = lng2 - lng1;
        double x = Math.sin(dLng) * Math.cos(lat2);
        double y = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        return Math.atan2(x, y);
    }
}
