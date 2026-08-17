package to.orbis.v2.polygons.creator.utils.points.points.creator;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonLineDto;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.utils.points.PointCommonUtils;
import to.orbis.v2.polygons.creator.utils.points.points.polylabel.PolyLabel;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PalindromeUtils {
    public static List<PointDto> merge(Set<PolygonLineDto> palindromeLineList) {
        GeometryFactory geometryFactory = new GeometryFactory();
        List<Polygon> jtsPolygons = palindromeLineList.stream()
                .filter(place -> place.getPolygon() != null && !place.getPolygon().isEmpty())
                .map(place -> createPolygonFromPoints(geometryFactory, place.getPolygon()))
                .collect(Collectors.toList());

        // Perform the union operation on all polygons
        Geometry geometry = CascadedPolygonUnion.union(jtsPolygons);
        if (geometry == null) {
            return List.of();
        } else if (geometry instanceof Polygon) {
            return convertPolygonToPoints((Polygon) geometry);
        } else {
            Polygon polygon = convertMultiPolygonToPolygon((MultiPolygon) geometry);
            return convertPolygonToPoints(polygon);
        }
    }

    /**
     * this was created so we can avoid exceptions because in some cases we would have multipolygons
     */
    private static Polygon convertMultiPolygonToPolygon(MultiPolygon multiPolygon) {
        // Simplified approach: take the largest polygon by area
        Polygon largestPolygon = null;
        double largestArea = -1;
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
            double area = polygon.getArea();
            if (area > largestArea) {
                largestArea = area;
                largestPolygon = polygon;
            }
        }

        return largestPolygon;
    }

    private static Polygon createPolygonFromPoints(GeometryFactory geometryFactory, List<PointDto> points) {
        Coordinate[] coordinates = points.stream()
                .map(point -> new Coordinate(point.getLongitude(), point.getLatitude()))
                .toArray(Coordinate[]::new);

        // Ensure the polygon is closed
        if (!coordinates[0].equals(coordinates[coordinates.length - 1])) {
            Coordinate[] closedCoordinates = new Coordinate[coordinates.length + 1];
            System.arraycopy(coordinates, 0, closedCoordinates, 0, coordinates.length);
            closedCoordinates[closedCoordinates.length - 1] = coordinates[0];
            coordinates = closedCoordinates;
        }

        return geometryFactory.createPolygon(coordinates);
    }

    private static List<PointDto> convertPolygonToPoints(Polygon polygon) {
        return convertCoordinateToPoints(List.of(polygon.getCoordinates()));
    }

    private static List<PointDto> convertCoordinateToPoints(List<Coordinate> coordinates) {
        List<PointDto> points = new ArrayList<>();
        for (Coordinate coordinate : coordinates) {
            points.add(new PointDto(coordinate.x, coordinate.y));
        }
        return points;
    }

    public static PointDto findPoleOfInaccessibility(List<PointDto> polygonPoints) {
        GeometryFactory geometryFactory = new GeometryFactory();
        Polygon polygon = createPolygonFromPoints(geometryFactory, polygonPoints);
        double precision = Math.abs(
                polygonPoints.get(0).getLatitude() - polygonPoints.get(1).getLatitude()) * 10;

//        List<Coordinate> pointsInsidePolygon = findAllPointsInsidePolygon(polygon, precision, geometryFactory);

        Coordinate coordinate = findPoleOfInaccessibility(polygon, precision, geometryFactory);
        return new PointDto(coordinate.x, coordinate.y);
    }


    private static Coordinate findPoleOfInaccessibility(
            Polygon polygon, double precision, GeometryFactory geometryFactory) {
        double minX = polygon.getEnvelopeInternal().getMinX();
        double minY = polygon.getEnvelopeInternal().getMinY();
        double maxX = polygon.getEnvelopeInternal().getMaxX();
        double maxY = polygon.getEnvelopeInternal().getMaxY();

        // List to store points inside the polygon
        List<Coordinate> pointsInsidePolygon = new ArrayList<>();

        Coordinate poleOfInaccessibility = null;
        double maxDistance = -1;

        // Generate points with the specified precision
        for (double x = minX; x <= maxX; x += precision) {
            for (double y = minY; y <= maxY; y += precision) {
                Coordinate point = new Coordinate(x, y);
                Point pointGeometry = geometryFactory.createPoint(point);
                if (polygon.contains(pointGeometry)) {
                    pointsInsidePolygon.add(point);
                    double distance = minimumDistanceToPolygonEdges(polygon, point);
                    if (distance > maxDistance) {
                        maxDistance = distance;
                        poleOfInaccessibility = point;
                    }
                }
            }
        }

        return poleOfInaccessibility;
    }

    public static double minimumDistanceToPolygonEdges(Polygon polygon, Coordinate point) {
        double minDistance = Double.MAX_VALUE;
        Coordinate[] coordinates = polygon.getCoordinates();

        for (int i = 0; i < coordinates.length - 1; i++) {
            Coordinate edgeStart = coordinates[i];
            Coordinate edgeEnd = coordinates[i + 1];
            double distance = pointToSegmentDistance(point, edgeStart, edgeEnd);
            if (distance < minDistance) {
                minDistance = distance;
            }
        }

        return minDistance;
    }

    public static double pointToSegmentDistance(Coordinate point, Coordinate edgeStart, Coordinate edgeEnd) {
        double lat1 = edgeStart.y;
        double lon1 = edgeStart.x;
        double lat2 = edgeEnd.y;
        double lon2 = edgeEnd.x;
        double lat3 = point.y;
        double lon3 = point.x;

        double a = haversineDistance(lat1, lon1, lat3, lon3);
        double b = haversineDistance(lat2, lon2, lat3, lon3);
        double c = haversineDistance(lat1, lon1, lat2, lon2);

        if (a >= b + c) return b;
        if (b >= a + c) return a;

        double s = (a + b + c) / 2;
        double area = Math.sqrt(s * (s - a) * (s - b) * (s - c));
        return 2 * area / c;
    }

    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Radius of the Earth in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // Distance in meters
    }


    public static PointDto findPoleOfInaccessibilityFromLib(List<PointDto> polygonPoints) {
        Double[][][] array = convertToPolygon(polygonPoints);

        double precision = Math.abs(array[0][0][0] - array[0][1][0]);
        PolyLabel result = PolyLabel.polyLabel(array, precision);

        double lng = PointCommonUtils.toDegrees(result.getX());
        double lat = PointCommonUtils.toDegrees(result.getY());
        return PointDto.builder()
                .latitude(lat)
                .longitude(lng)
                .build();
    }

    public static Double[][][] convertToPolygon(List<PointDto> polygonPoints) {
        Double[][][] polygon = new Double[1][polygonPoints.size()][2];

        for (int i = 0; i < polygonPoints.size(); i++) {
            PointDto point = polygonPoints.get(i);
            polygon[0][i][0] = PointCommonUtils.toRadians(point.getLongitude());
            polygon[0][i][1] = PointCommonUtils.toRadians(point.getLatitude());
        }

        return polygon;
    }

    public static boolean isPolygonInsidePolygon(List<PointDto> polygon1, List<PointDto> polygon2) {
        Path2D poly2 = createPolygon(polygon2);

        // Check if all points of polygon1 are inside polygon2
        for (PointDto point : polygon1) {
            if (!poly2.contains(point.getLongitude(), point.getLatitude())) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPointInsidePolygon(List<PointDto> polygon, PointDto point) {
        Path2D poly = createPolygon(polygon);

        return poly.contains(point.getLongitude(), point.getLatitude());
    }

    private static Path2D createPolygon(List<PointDto> points) {
        Path2D polygon = new Path2D.Double();
        polygon.moveTo(points.get(0).getLongitude(), points.get(0).getLatitude());

        for (int i = 1; i < points.size(); i++) {
            polygon.lineTo(points.get(i).getLongitude(), points.get(i).getLatitude());
        }
        polygon.closePath();
        return polygon;
    }
}
