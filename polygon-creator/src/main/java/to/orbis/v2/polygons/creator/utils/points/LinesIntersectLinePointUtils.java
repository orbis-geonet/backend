package to.orbis.v2.polygons.creator.utils.points;

import lombok.experimental.UtilityClass;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonLineDto;
import to.orbis.v2.polygons.creator.models.dto.PlaceForPalindromeCreationDto;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.TangentPoints;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@UtilityClass
public class LinesIntersectLinePointUtils {

    public static void checkPalindromeCollisionAndBreakConnection(
            List<PlaceForPalindromeCreationDto> places) {
        for (int i = 0; i < places.size(); i++) {
            if (isNotPalindrome(places.get(i))) {
                continue;
            }

            for (int j = 0; j < places.size(); j++) {
                if (i == j) {
                    continue;
                }

                if (isNotPalindrome(places.get(j))) {
                    for (PolygonLineDto tangentPoint : places.get(i).getTangentPoints()) {
                        if (tangentPoint.isShow) {
                            boolean centerLineIntersect = LineIntersectCirclePointUtils.doesLineIntersectCircle(
                                    places.get(j).getCoordinates(), places.get(j).getSize(), tangentPoint.getTangentPointsCenters());

                            boolean isShow = !centerLineIntersect;
                            tangentPoint.setShow(isShow);
                            if (!isShow) {
                                Set<String> shouldConnectionBreakExternal = new HashSet<>();
                                shouldConnectionBreakExternal.add(tangentPoint.getPlaceKeyFirst());
                                shouldConnectionBreakExternal.add(tangentPoint.getPlaceKeySecond());

                                breakPolygonConnection(places, shouldConnectionBreakExternal);
                            }
                        }
                    }
                } else if (!places.get(i).getDominantGroupKey().equals(places.get(j).getDominantGroupKey())) {
                        for (PolygonLineDto tangentPoint : places.get(i).getTangentPoints()) {
                            if (tangentPoint.isShow) {
                                boolean isShow = isShow(tangentPoint, places.get(j));
                                tangentPoint.setShow(isShow);
                                if (!isShow) {
                                    Set<String> shouldConnectionBreakExternal = new HashSet<>();
                                    shouldConnectionBreakExternal.add(tangentPoint.getPlaceKeyFirst());
                                    shouldConnectionBreakExternal.add(tangentPoint.getPlaceKeySecond());

                                    breakPolygonConnection(places, shouldConnectionBreakExternal);
                                }
                            }
                        }
                }
            }
        }
    }

    private boolean isShow (
            PolygonLineDto tangentPoint, PlaceForPalindromeCreationDto place
    ) {

        boolean isTouchedAnyInternalLine = tangentPoint.isTouchedInternalLineFirst() || tangentPoint.isTouchedInternalLineSecond();

        if (isTouchedAnyInternalLine) {
            return false;
        }

        boolean firstExternalLineIntersect = doesLinesIntersectLine(tangentPoint.getTangentExternalLineFirst(), place.getTangentPoints());
        boolean secondExternalLineIntersect = doesLinesIntersectLine(tangentPoint.getTangentExternalLineSecond(), place.getTangentPoints());
        boolean firstInternalLineIntersect = doesLinesIntersectLine(tangentPoint.getTangentInternalLineFirst(), place.getTangentPoints());
        boolean secondInternalLineIntersect = doesLinesIntersectLine(tangentPoint.getTangentInternalLineSecond(), place.getTangentPoints());

        boolean isNotShow = (firstExternalLineIntersect && secondExternalLineIntersect) ||
                firstInternalLineIntersect || secondInternalLineIntersect;
        return !isNotShow;
    }

    private void breakPolygonConnection (
            List<PlaceForPalindromeCreationDto> places,
            Set<String> shouldConnectionBreak
    ) {
        for (PlaceForPalindromeCreationDto place : places) {
            List<Set<String>> connectingPlaces = place.getConnectingPlacesByPair();
            connectingPlaces.removeIf(set -> set.equals(shouldConnectionBreak));
        }
    }

    private boolean isNotPalindrome(PlaceForPalindromeCreationDto place) {
        return place.getTangentPoints() == null || place.getTangentPoints().isEmpty();
    }

    private static boolean doesLinesIntersectLine(
            TangentPoints tangentPoints, Set<PolygonLineDto> palindromeLines) {
        //should return false if NO connection
        return palindromeLines.stream()
                .filter(it -> it.isShow)
                .anyMatch(otherLine -> {
                    boolean firstLineIntersect = doesLineIntersectLine(tangentPoints, otherLine.getTangentExternalLineFirst());
                    boolean secondLineIntersect = doesLineIntersectLine(tangentPoints, otherLine.getTangentExternalLineSecond());
                    return firstLineIntersect || secondLineIntersect;
                });
    }

    public static boolean doesLineIntersectLine(
            TangentPoints tangentPoints1, TangentPoints tangentPoints2) {
        // Convert degrees to radians for calculation
        double lat1Rad = PointCommonUtils.toRadians(tangentPoints1.firstCirclePoint.getLatitude());
        double lon1Rad = PointCommonUtils.toRadians(tangentPoints1.firstCirclePoint.getLongitude());
        double lat2Rad = PointCommonUtils.toRadians(tangentPoints1.secondCircePoint.getLatitude());
        double lon2Rad = PointCommonUtils.toRadians(tangentPoints1.secondCircePoint.getLongitude());
        double lat3Rad = PointCommonUtils.toRadians(tangentPoints2.firstCirclePoint.getLatitude());
        double lon3Rad = PointCommonUtils.toRadians(tangentPoints2.firstCirclePoint.getLongitude());
        double lat4Rad = PointCommonUtils.toRadians(tangentPoints2.secondCircePoint.getLatitude());
        double lon4Rad = PointCommonUtils.toRadians(tangentPoints2.secondCircePoint.getLongitude());

        // Convert coordinates to Cartesian coordinates for calculation
        double[] p1 = latLonToCartesian(lat1Rad, lon1Rad);
        double[] p2 = latLonToCartesian(lat2Rad, lon2Rad);
        double[] p3 = latLonToCartesian(lat3Rad, lon3Rad);
        double[] p4 = latLonToCartesian(lat4Rad, lon4Rad);

        return checkLineIntersection(p1, p2, p3, p4);
    }

    public PointDto findConnectionPoint(
            TangentPoints tangentPoints1, TangentPoints tangentPoints2) {
        double lat1Rad = PointCommonUtils.toRadians(tangentPoints1.firstCirclePoint.getLatitude());
        double lon1Rad = PointCommonUtils.toRadians(tangentPoints1.firstCirclePoint.getLongitude());
        double lat2Rad = PointCommonUtils.toRadians(tangentPoints1.secondCircePoint.getLatitude());
        double lon2Rad = PointCommonUtils.toRadians(tangentPoints1.secondCircePoint.getLongitude());
        double lat3Rad = PointCommonUtils.toRadians(tangentPoints2.firstCirclePoint.getLatitude());
        double lon3Rad = PointCommonUtils.toRadians(tangentPoints2.firstCirclePoint.getLongitude());
        double lat4Rad = PointCommonUtils.toRadians(tangentPoints2.secondCircePoint.getLatitude());
        double lon4Rad = PointCommonUtils.toRadians(tangentPoints2.secondCircePoint.getLongitude());

        // Calculate the coefficients of the lines (A * x + B * y = C)
        double A1 = lat2Rad - lat1Rad;
        double B1 = lon1Rad - lon2Rad;
        double C1 = A1 * lon1Rad + B1 * lat1Rad;

        double A2 = lat4Rad - lat3Rad;
        double B2 = lon3Rad - lon4Rad;
        double C2 = A2 * lon3Rad + B2 * lat3Rad;

        // Calculate the determinant
        double determinant = A1 * B2 - A2 * B1;

        // Check if lines are parallel
        if (determinant == 0) {
            // Lines are parallel; return null or handle accordingly
            return null;
        } else {
            // Lines intersect
            double x = (B2 * C1 - B1 * C2) / determinant;
            double y = (A1 * C2 - A2 * C1) / determinant;

            // Convert back from radians to degrees
            double latitude = PointCommonUtils.toDegrees(y);
            double longitude = PointCommonUtils.toDegrees(x);

            // Return the intersection point
            return PointDto.builder()
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();
        }
    }

//    public PointDto findConnectionPoint(
//            PointDto startLine1, PointDto endLine1,
//            PointDto startLine2, PointDto endLine2
//    ) {
//
//    }

    private static boolean checkLineIntersection(double[] p1, double[] p2, double[] p3, double[] p4) {
        double d1 = direction(p3, p4, p1);
        double d2 = direction(p3, p4, p2);
        double d3 = direction(p1, p2, p3);
        double d4 = direction(p1, p2, p4);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }

        if (d1 == 0 && onSegment(p3, p4, p1)) return true;
        if (d2 == 0 && onSegment(p3, p4, p2)) return true;
        if (d3 == 0 && onSegment(p1, p2, p3)) return true;
        if (d4 == 0 && onSegment(p1, p2, p4)) return true;

        return false;
    }

    private static double direction(double[] pi, double[] pj, double[] pk) {
        return (pk[0] - pi[0]) * (pj[1] - pi[1]) - (pj[0] - pi[0]) * (pk[1] - pi[1]);
    }

    private static boolean onSegment(double[] pi, double[] pj, double[] pk) {
        return Math.min(pi[0], pj[0]) <= pk[0] && pk[0] <= Math.max(pi[0], pj[0]) &&
                Math.min(pi[1], pj[1]) <= pk[1] && pk[1] <= Math.max(pi[1], pj[1]);
    }

    private static double[] latLonToCartesian(double latRad, double lonRad) {
        double x = Math.cos(latRad) * Math.cos(lonRad);
        double y = Math.cos(latRad) * Math.sin(lonRad);
        double z = Math.sin(latRad);
        return new double[]{x, y, z};
    }
}
