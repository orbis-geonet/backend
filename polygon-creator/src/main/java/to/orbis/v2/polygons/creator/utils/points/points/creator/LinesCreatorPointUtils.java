package to.orbis.v2.polygons.creator.utils.points.points.creator;

import lombok.experimental.UtilityClass;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.TangentPoints;
import to.orbis.v2.polygons.creator.utils.points.PointCommonUtils;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class LinesCreatorPointUtils {

    public static List<PointDto> interpolatePoints(TangentPoints tangentPoints) {
        return interpolatePoints(
                tangentPoints.getFirstCirclePoint().getLongitude(),
                tangentPoints.getFirstCirclePoint().getLatitude(),
                tangentPoints.getSecondCircePoint().getLongitude(),
                tangentPoints.getSecondCircePoint().getLatitude()
        );
    }

    public static List<PointDto> interpolatePoints(
            PointDto firstPoint,
            PointDto secondPoint
    ) {
        return interpolatePoints(
                firstPoint.getLongitude(),
                firstPoint.getLatitude(),
                secondPoint.getLongitude(),
                secondPoint.getLatitude()
        );
    }

    private static List<PointDto> interpolatePoints(
            Double longitudeFirstPoint,
            Double latitudeFirstPoint,
            Double longitudeSecondPoint,
            Double latitudeSecondPoint
    ) {
        List<PointDto> points = new ArrayList<>();
        double lat1 = PointCommonUtils.toRadians(latitudeFirstPoint);
        double lon1 = PointCommonUtils.toRadians(longitudeFirstPoint);
        double lat2 = PointCommonUtils.toRadians(latitudeSecondPoint);
        double lon2 = PointCommonUtils.toRadians(longitudeSecondPoint);

        PointDto pointFirstDto = PointDto.builder()
                .latitude(latitudeFirstPoint)
                .longitude(longitudeFirstPoint)
                .build();

        points.add(pointFirstDto);
        for (int i = 1; i <= PointCommonUtils.LINE_NUM_POINTS - 1; i++) {
            double fraction = (double) i / PointCommonUtils.LINE_NUM_POINTS;
            double deltaLat = lat2 - lat1;
            double deltaLon = lon2 - lon1;
            double a = Math.sin((1 - fraction) * deltaLat / 2) * Math.sin((1 - fraction) * deltaLat / 2) +
                    Math.cos(lat1) * Math.cos(lat2) *
                            Math.sin((1 - fraction) * deltaLon / 2) * Math.sin((1 - fraction) * deltaLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

            double A = Math.sin((1 - fraction) * c) / Math.sin(c);
            double B = Math.sin(fraction * c) / Math.sin(c);
            double x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2);
            double y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2);
            double z = A * Math.sin(lat1) + B * Math.sin(lat2);

            double interpolatedLat = PointCommonUtils.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y)));
            double interpolatedLon = PointCommonUtils.toDegrees(Math.atan2(y, x));
            PointDto pointDto = PointDto.builder()
                    .latitude(interpolatedLat)
                    .longitude(interpolatedLon)
                    .build();
            points.add(pointDto);
        }
        PointDto pointSecondDto = PointDto.builder()
                .latitude(latitudeSecondPoint)
                .longitude(longitudeSecondPoint)
                .build();
        points.add(pointSecondDto);

        return points;
    }
}
