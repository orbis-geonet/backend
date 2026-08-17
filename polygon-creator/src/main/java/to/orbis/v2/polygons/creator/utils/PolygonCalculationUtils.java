package to.orbis.v2.polygons.creator.utils;

import lombok.experimental.UtilityClass;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;
import to.orbis.v2.polygons.creator.models.entity.PolygonSchedulerCoordinate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType.TRIGGER;

/**
 * Helper class mostly used for testing and to provide coordinates and similar.
 */
@UtilityClass
public class PolygonCalculationUtils {

    public static final double RADIUS_KM = 150.0; // used recalculating the whole map
    public static final double RADIUS_KM_CHECK_IN = 25.0; // used for checkin

    private static final double KM_PER_DEGREE = 111.0;  // Approximate value for both latitude and longitude
    private static final double RADIUS_LAT = RADIUS_KM / KM_PER_DEGREE;

    public static final double EARTH_RADIUS_KM = 6378.1;

    // North America
    public static final double LAT_NORTH_NORTH_AMERICA = 71.38;
    public static final double LAT_SOUTH_NORTH_AMERICA = 7.20;
    public static final double LON_WEST_NORTH_AMERICA = -168.05;
    public static final double LON_EAST_NORTH_AMERICA = -52.23;

    // South America
    public static final double LAT_NORTH_SOUTH_AMERICA = 12.5;
    public static final double LAT_SOUTH_SOUTH_AMERICA = -55.98;
    public static final double LON_WEST_SOUTH_AMERICA = -81.33;
    public static final double LON_EAST_SOUTH_AMERICA = -34.79;

    // Europe
    public static final double LAT_NORTH_EUROPE = 71.18;
    public static final double LAT_SOUTH_EUROPE = 34.50;
    public static final double LON_WEST_EUROPE = -25.67;
    public static final double LON_EAST_EUROPE = 44.82;

    // Africa
    public static final double LAT_NORTH_AFRICA = 37.34;
    public static final double LAT_SOUTH_AFRICA = -34.83;
    public static final double LON_WEST_AFRICA = -17.54;
    public static final double LON_EAST_AFRICA = 51.41;

    // Asia
    public static final double LAT_NORTH_ASIA = 77.72;
    public static final double LAT_SOUTH_ASIA = -10.75;
    public static final double LON_WEST_ASIA = 26.04;
    public static final double LON_EAST_ASIA = 169.45;

    // Australia (Oceania)
    public static final double LAT_NORTH_AUSTRALIA = -9.22;
    public static final double LAT_SOUTH_AUSTRALIA = -55.05;
    public static final double LON_WEST_AUSTRALIA = 112.92;
    public static final double LON_EAST_AUSTRALIA = 179.96;

    // Global Boundaries (excluding Antarctica)
    public static final double LAT_NORTH_WORLD = 77.72; // Northernmost point in Asia
    public static final double LAT_SOUTH_WORLD = -55.98; // Southernmost point in South America
    public static final double LON_WEST_WORLD = -168.05; // Westernmost point in North America
    public static final double LON_EAST_WORLD = 179.96;

    public static final double LAT_MAX = 90.0;
    public static final double LAT_MIN = -90.0;
    public static final double LON_MAX = 180.0;
    public static final double LON_MIN = -180.0;

    public static List<PolygonSchedulerCoordinate> getGlobalCoordinates() {
        return getCoordinates(LAT_NORTH_WORLD, LAT_SOUTH_WORLD, LON_EAST_WORLD, LON_WEST_WORLD);
    }

    public static List<PolygonSchedulerCoordinate> getSouthAmericaCoordinates() {
        return getCoordinates(LAT_NORTH_SOUTH_AMERICA, LAT_SOUTH_SOUTH_AMERICA, LON_EAST_SOUTH_AMERICA, LON_WEST_SOUTH_AMERICA);
    }

    public static List<PolygonSchedulerCoordinate> getCoordinates(
            double latNorth, double latSouth, double latEast, double latWest
    ) {
        double avgLat = (latNorth + latSouth) / 2;
        double kmPerDegreeLon = KM_PER_DEGREE * Math.cos(Math.toRadians(avgLat));
        double radiusLon = RADIUS_KM / kmPerDegreeLon;

        List<PolygonSchedulerCoordinate> coordinates = new ArrayList<>();

        for (double lat = latSouth; lat <= latNorth; lat += 2 * RADIUS_LAT) {
            for (double lon = latWest; lon <= latEast; lon += 2 * radiusLon) {
                coordinates.add(createPolygonSchedulerCoordinate(lon, lat, RADIUS_KM, TRIGGER));
                // Add an offset circle to create a hexagonal grid pattern
                coordinates.add(createPolygonSchedulerCoordinate(lon + radiusLon, lat + RADIUS_LAT, RADIUS_KM, TRIGGER));
            }
        }
        return coordinates.stream()
                .filter(it ->
                        it.getCoordinates().getX() < LON_MAX && it.getCoordinates().getX() > LON_MIN &&
                                it.getCoordinates().getY() < LAT_MAX && it.getCoordinates().getY() > LAT_MIN
                ).toList();
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
