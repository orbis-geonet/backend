package to.orbis.v2.polygons.creator.utils.points;

import lombok.experimental.UtilityClass;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonLineDto;
import to.orbis.v2.polygons.creator.models.dto.PlaceForPalindromeCreationDto;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.TangentPoints;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static to.orbis.v2.polygons.creator.utils.points.LineIntersectCirclePointUtils.doesLineIntersectCircle;

@UtilityClass
public class TangentPointsUtils {

    public void calculateTangentPointsAndSetConnectingPlaces(
            List<PlaceForPalindromeCreationDto> placeDtoList) {
        for (int i = 0; i < placeDtoList.size(); i++) {
            for (int j = 0; j < placeDtoList.size(); j++) {
                if (i == j) {
                    continue;
                }
                if (!placeDtoList.get(i).getDominantGroupKey().equals(placeDtoList.get(j).getDominantGroupKey())) {
                    continue;
                }

                boolean isShouldConnect = LinesCalculationPointUtils.shouldConnectByDistance(
                        placeDtoList.get(i).getCoordinates(), placeDtoList.get(i).getSize(),
                        placeDtoList.get(j).getCoordinates(), placeDtoList.get(j).getSize()
                );
                if (isShouldConnect) {
                    //Set connecting places if there is connection
                    setConnectingPlacesSet(placeDtoList.get(i), placeDtoList.get(j));

                    //Center points
                    TangentPoints tangentPointsCenters = createCenterTangentPoints(
                            placeDtoList.get(i), placeDtoList.get(j));

                    //External
                    List<TangentPoints> tangentPoints = LinesCalculationPointUtils.calculateExternalTangents(
                            placeDtoList.get(i).getCoordinates(), placeDtoList.get(i).getSize(),
                            placeDtoList.get(j).getCoordinates(), placeDtoList.get(j).getSize()
                    );
                    TangentPoints tangentPoints1 = tangentPoints.get(0);
                    TangentPoints tangentPoints2 = tangentPoints.get(1);

                    //Internal
                    List<TangentPoints> tangentInternalPoints = LinesCalculationPointUtils.calculateInternalTangents(
                            placeDtoList.get(i).getCoordinates(), placeDtoList.get(i).getSize(),
                            placeDtoList.get(j).getCoordinates(), placeDtoList.get(j).getSize()
                    );

                    TangentPoints tangentPointsInternal1 = tangentInternalPoints.get(0);
                    TangentPoints tangentPointsInternal2 = tangentInternalPoints.get(1);

                    PointDto tangentPointsInternalConnection = LinesIntersectLinePointUtils.findConnectionPoint(
                            tangentPointsInternal1, tangentPointsInternal2);

                    PolygonLineDto lineDto = PolygonLineDto.builder()
                            .placeKeyFirst(placeDtoList.get(i).getPlaceKey())
                            .placeKeySecond(placeDtoList.get(j).getPlaceKey())
                            .tangentExternalLineFirst(tangentPoints1)
                            .tangentExternalLineSecond(tangentPoints2)
                            .tangentInternalLineSecond(tangentPointsInternal1)
                            .tangentInternalLineFirst(tangentPointsInternal2)
                            .tangentInternalLinesConnectionPoint(tangentPointsInternalConnection)
                            .isShow(true)
                            .tangentPointsCenters(tangentPointsCenters)
                            .circleRadios1(placeDtoList.get(i).getSize())
                            .circleCenterPoint1(placeDtoList.get(i).getCoordinates())
                            .circleRadios2(placeDtoList.get(j).getSize())
                            .circleCenterPoint2(placeDtoList.get(j).getCoordinates())
                            .build();

                    checkCollisionLineAndCircle(lineDto, placeDtoList.get(i).getDominantGroupKey(), placeDtoList);

                    addLine(lineDto, placeDtoList.get(i));
                }
            }
        }
    }

    private void addLine(
            PolygonLineDto lineDto, PlaceForPalindromeCreationDto placeDto) {
        if (placeDto.getTangentPoints() == null) {
            placeDto.setTangentPoints(new HashSet<>());
        }
        placeDto.getTangentPoints().add(lineDto);
    }

    private static void setConnectingPlacesSet(
            PlaceForPalindromeCreationDto firstPlace, PlaceForPalindromeCreationDto secondPlace) {
        Set<String> placesSet = new HashSet<>();
        placesSet.add(firstPlace.getPlaceKey());
        placesSet.add(secondPlace.getPlaceKey());

        firstPlace.getConnectingPlacesByPair().add(placesSet);
        secondPlace.getConnectingPlacesByPair().add(placesSet);
    }

    private void checkCollisionLineAndCircle(
            PolygonLineDto lineDto,
            String domainGroupKey,
            List<PlaceForPalindromeCreationDto> allPlaceDtoList) {
        allPlaceDtoList.stream()
                .filter(it -> !it.getDominantGroupKey().equals(domainGroupKey))
                .forEach(it -> {
                    boolean firstExternalLineIntersect = doesLineIntersectCircle(it.getCoordinates(), it.getSize(), lineDto.tangentExternalLineFirst);
                    boolean secondExternalLineIntersect = doesLineIntersectCircle(it.getCoordinates(), it.getSize(), lineDto.tangentExternalLineSecond);

                    if (firstExternalLineIntersect) {
                        lineDto.setTouchedExternalLineFirst(true);
                    }
                    if (secondExternalLineIntersect) {
                        lineDto.setTouchedExternalLineSecond(true);
                    }

                    boolean firstInternalLineIntersect = doesLineIntersectCircle(it.getCoordinates(), it.getSize(), lineDto.tangentInternalLineFirst);
                    boolean secondInternalLineIntersect = doesLineIntersectCircle(it.getCoordinates(), it.getSize(), lineDto.tangentInternalLineSecond);

                    if (firstInternalLineIntersect) {
                        lineDto.setTouchedInternalLineFirst(true);
                    }
                    if (secondInternalLineIntersect) {
                        lineDto.setTouchedInternalLineSecond(true);
                    }
                });
    }

    private TangentPoints createCenterTangentPoints(
            PlaceForPalindromeCreationDto firstPlace, PlaceForPalindromeCreationDto secondPlace) {
        return TangentPoints.builder()
                .firstCirclePoint(firstPlace.getCoordinates())
                .secondCircePoint(secondPlace.getCoordinates())
                .build();
    }
}
