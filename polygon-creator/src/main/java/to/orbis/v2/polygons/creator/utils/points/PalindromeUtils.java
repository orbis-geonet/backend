package to.orbis.v2.polygons.creator.utils.points;

import lombok.experimental.UtilityClass;
import to.orbis.v2.polygons.creator.models.dto.PointDto;

import java.util.*;

@UtilityClass
public class PalindromeUtils {

    public static List<PointDto> findConvexHull(List<PointDto> points) {
        if (points.size() < 3) throw new IllegalArgumentException("At least 3 points are required");

        // Step 1: Find the point with the lowest latitude (and leftmost in case of a tie)
        PointDto minYPoint = Collections.min(points, Comparator.comparing(PointDto::getLatitude)
                .thenComparing(PointDto::getLongitude));

        // Step 2: Sort the points based on the polar angle with respect to minYPoint
        points.sort((p1, p2) -> {
            double angle1 = Math.atan2(p1.getLatitude() - minYPoint.getLatitude(), p1.getLongitude() - minYPoint.getLongitude());
            double angle2 = Math.atan2(p2.getLatitude() - minYPoint.getLatitude(), p2.getLongitude() - minYPoint.getLongitude());
            return Double.compare(angle1, angle2);
        });

        // Step 3: Process points to construct the convex hull
        Stack<PointDto> stack = new Stack<>();
        stack.push(points.get(0));
        stack.push(points.get(1));

        for (int i = 2; i < points.size(); i++) {
            PointDto top = stack.pop();
            while (!stack.isEmpty() && orientation(stack.peek(), top, points.get(i)) != 2) {
                top = stack.pop();
            }
            stack.push(top);
            stack.push(points.get(i));
        }

        return new ArrayList<>(stack);
    }

    private static int orientation(PointDto p, PointDto q, PointDto r) {
        double val = (q.getLatitude() - p.getLatitude()) * (r.getLongitude() - q.getLongitude())
                - (q.getLongitude() - p.getLongitude()) * (r.getLatitude() - q.getLatitude());
        if (val == 0) return 0; // collinear
        return (val > 0) ? 1 : 2; // clock or counterclock wise
    }

    public static List<PointDto> findBoundaryPoints(List<PointDto> points) {
        if (points.size() < 3) throw new IllegalArgumentException("At least 3 points are required");

        // Step 1: Find the leftmost point
        PointDto startPoint = Collections.min(points, Comparator.comparing(PointDto::getLongitude));

        List<PointDto> boundaryPoints = new ArrayList<>();
        PointDto currentPoint = startPoint;
        PointDto nextPoint;

        do {
            boundaryPoints.add(currentPoint);
            nextPoint = points.get(0);

            for (PointDto point : points) {
                if (nextPoint == currentPoint || isLeftTurn(currentPoint, nextPoint, point)) {
                    nextPoint = point;
                }
            }

            currentPoint = nextPoint;
        } while (currentPoint != startPoint);

        return boundaryPoints;
    }

    private static boolean isLeftTurn(PointDto a, PointDto b, PointDto c) {
        return (b.getLongitude() - a.getLongitude()) * (c.getLatitude() - a.getLatitude())
                - (b.getLatitude() - a.getLatitude()) * (c.getLongitude() - a.getLongitude()) > 0;
    }


    public static List<PointDto> findConvexHull2(List<PointDto> points) {
        // Sort points lexicographically (by longitude, then by latitude)
        points.sort(Comparator.comparing(PointDto::getLongitude).thenComparing(PointDto::getLatitude));

        // Build the lower hull
        List<PointDto> lower = new ArrayList<>();
        for (PointDto p : points) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(p);
        }

        // Build the upper hull
        List<PointDto> upper = new ArrayList<>();
        for (int i = points.size() - 1; i >= 0; i--) {
            PointDto p = points.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(p);
        }

        // Concatenate lower and upper hull to form the convex hull
        // Remove the last point of each half because it's repeated at the beginning of the other half
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);

        return lower;
    }

    private static double cross(PointDto o, PointDto a, PointDto b) {
        return (a.getLongitude() - o.getLongitude()) * (b.getLatitude() - o.getLatitude()) -
                (a.getLatitude() - o.getLatitude()) * (b.getLongitude() - o.getLongitude());
    }


    public static List<PointDto> findConvexHull3(List<PointDto> points) {
        // Sort points lexicographically (by longitude, then by latitude)
        points.sort(Comparator.comparing(PointDto::getLongitude).thenComparing(PointDto::getLatitude));

        // Build the lower hull
        List<PointDto> lower = new ArrayList<>();
        for (PointDto p : points) {
            while (lower.size() >= 2 && cross3(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(p);
        }

        // Build the upper hull
        List<PointDto> upper = new ArrayList<>();
        for (int i = points.size() - 1; i >= 0; i--) {
            PointDto p = points.get(i);
            while (upper.size() >= 2 && cross3(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(p);
        }

        // Concatenate lower and upper hull to form the convex hull
        // Remove the last point of each half because it's repeated at the beginning of the other half
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);

        return lower;
    }

    private static double cross3(PointDto o, PointDto a, PointDto b) {
        return (a.getLongitude() - o.getLongitude()) * (b.getLatitude() - o.getLatitude()) -
                (a.getLatitude() - o.getLatitude()) * (b.getLongitude() - o.getLongitude());
    }


    public static List<PointDto> findBorderPoints(List<PointDto> points) {
        List<PointDto> borderPoints = new ArrayList<>();

        for (PointDto point : points) {
            boolean hasTop = false, hasBottom = false, hasLeft = false, hasRight = false;

            for (PointDto other : points) {
                if (point != other) {
                    if (other.getLongitude().equals(point.getLongitude()) && other.getLatitude() > point.getLatitude()) {
                        hasTop = true;
                    }
                    if (other.getLongitude().equals(point.getLongitude()) && other.getLatitude() < point.getLatitude()) {
                        hasBottom = true;
                    }
                    if (other.getLatitude().equals(point.getLatitude()) && other.getLongitude() < point.getLongitude()) {
                        hasLeft = true;
                    }
                    if (other.getLatitude().equals(point.getLatitude()) && other.getLongitude() > point.getLongitude()) {
                        hasRight = true;
                    }
                }
            }

            if (!hasTop || !hasBottom || !hasLeft || !hasRight) {
                borderPoints.add(point);
            }
        }

        return borderPoints;
    }

    public static List<PointDto> findBorderPoints2(List<PointDto> points) {
        List<PointDto> borderPoints = new ArrayList<>();
        double tolerance = 3.0; // Допустимое отклонение в градусах

        for (PointDto point : points) {
            boolean hasTop = false, hasBottom = false, hasLeft = false, hasRight = false;

            for (PointDto other : points) {
                if (!point.equals(other)) {
                    double deltaX = other.getLongitude() - point.getLongitude();
                    double deltaY = other.getLatitude() - point.getLatitude();
                    double angle = Math.toDegrees(Math.atan2(deltaY, deltaX));

                    if (Math.abs(deltaX) <= tolerance && other.getLatitude() > point.getLatitude()) {
                        hasTop = true;
                    }
                    if (Math.abs(deltaX) <= tolerance && other.getLatitude() < point.getLatitude()) {
                        hasBottom = true;
                    }
                    if (Math.abs(deltaY) <= tolerance && other.getLongitude() < point.getLongitude()) {
                        hasLeft = true;
                    }
                    if (Math.abs(deltaY) <= tolerance && other.getLongitude() > point.getLongitude()) {
                        hasRight = true;
                    }
                }
            }

            if (!hasTop || !hasBottom || !hasLeft || !hasRight) {
                borderPoints.add(point);
            }
        }

        return borderPoints;
    }
}
