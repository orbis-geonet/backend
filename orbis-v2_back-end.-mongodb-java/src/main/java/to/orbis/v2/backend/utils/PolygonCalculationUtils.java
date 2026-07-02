package to.orbis.v2.backend.utils;

import lombok.experimental.UtilityClass;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import to.orbis.v2.backend.models.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.backend.models.PolygonSchedulerCoordinateType;
import to.orbis.v2.backend.models.entity.PolygonSchedulerCoordinate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static to.orbis.v2.backend.models.PolygonSchedulerCoordinateType.TRIGGER;

/**
 * Helper class mostly used for testing and to provide coordinates and similar.
 */
@UtilityClass
public class PolygonCalculationUtils {

    public static final double RADIUS_KM = 150.0; // used recalculating the whole map
    public static final double RADIUS_KM_CHECK_IN = 10.0; // used for checkin

    private static final double KM_PER_DEGREE = 111.0;  // Approximate value for both latitude and longitude
    private static final double RADIUS_LAT = RADIUS_KM / KM_PER_DEGREE;

    public static final double EARTH_RADIUS_KM = 6378.1;

    public static List<PolygonSchedulerCoordinate> getSouthAmericaCoordinates() {
        double LAT_NORTH = 12.5;
        double LAT_SOUTH = -55.98;
        double LON_WEST = -81.33;
        double LON_EAST = -34.79;

        double avgLat = (LAT_NORTH + LAT_SOUTH) / 2;
        double kmPerDegreeLon = KM_PER_DEGREE * Math.cos(Math.toRadians(avgLat));
        double radiusLon = RADIUS_KM / kmPerDegreeLon;

        List<PolygonSchedulerCoordinate> coordinates = new ArrayList<>();

        for (double lat = LAT_SOUTH; lat <= LAT_NORTH; lat += 2 * RADIUS_LAT) {
            for (double lon = LON_WEST; lon <= LON_EAST; lon += 2 * radiusLon) {
                coordinates.add(createPolygonSchedulerCoordinate(lon, lat, RADIUS_KM, TRIGGER));
                // Add an offset circle to create a hexagonal grid pattern
                coordinates.add(createPolygonSchedulerCoordinate(lon + radiusLon, lat + RADIUS_LAT, RADIUS_KM, TRIGGER));
            }
        }
        return coordinates;
    }

    public static List<PolygonSchedulerCoordinate> getPolygonTestCoordinates() {
        return Arrays.asList(
                createPolygonSchedulerCoordinate(-56.18816, -34.90328, 100, TRIGGER),
                createPolygonSchedulerCoordinate(-58.504532, -34.622322, 100, TRIGGER)
        );
    }

    public static PolygonSchedulerCoordinate createPolygonSchedulerCoordinate(
            double longitude, double latitude, double radius, PolygonSchedulerCoordinateType type
    ) {
        ObjectId id = new ObjectId();
        return PolygonSchedulerCoordinate.builder()
                .id(id)
                .polygonSchedulerCoordinateKey(id.toHexString())
                .longitude(longitude)
                .latitude(latitude)
                .coordinates(new GeoJsonPoint(longitude, latitude))
                .radius(radius)
                .status(PolygonSchedulerCoordinateStatus.NEW)
                .type(type)
                .isEnabled(true)
                .isCalculated(false)
                .numberOfPolygons(0)
                .numberOfPolygons(0)
                .createdAt(Instant.now())
                .build();
    }
}
