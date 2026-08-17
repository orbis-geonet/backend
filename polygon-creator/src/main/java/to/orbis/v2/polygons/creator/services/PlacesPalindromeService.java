package to.orbis.v2.polygons.creator.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;
import to.orbis.v2.polygons.creator.models.dto.polygon.CirclePolygonDto;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonsDto;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonLineDto;
import to.orbis.v2.polygons.creator.models.dto.polygon.PolygonLineWithPointsDto;
import to.orbis.v2.polygons.creator.models.dto.PlaceForPalindromeCreationDto;
import to.orbis.v2.polygons.creator.models.dto.PlacePalindromeCreationDto;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.utils.places.PlaceIntersectionUtils;
import to.orbis.v2.polygons.creator.utils.points.points.creator.CircleCreatorPointUtils;
import to.orbis.v2.polygons.creator.utils.points.points.creator.LinesCreatorPointUtils;
import to.orbis.v2.polygons.creator.utils.points.points.creator.PalindromeUtils;
import to.orbis.v2.polygons.creator.utils.points.LinesIntersectLinePointUtils;
import to.orbis.v2.polygons.creator.utils.points.PointCommonUtils;
import to.orbis.v2.polygons.creator.utils.points.TangentPointsUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacesPalindromeService {

    public List<PlacePalindromeCreationDto> createListWithPalindrome(
            List<PlaceForPalindromeCreationDto> allPlaceDtoListNew, PolygonSchedulerCoordinateType type) {

        //Calculate TangentPoints
        log.info("Type: {}. Places {} after resized", type, allPlaceDtoListNew.size());
        TangentPointsUtils.calculateTangentPointsAndSetConnectingPlaces(allPlaceDtoListNew);
        LinesIntersectLinePointUtils.checkPalindromeCollisionAndBreakConnection(allPlaceDtoListNew);

        List<List<PlaceForPalindromeCreationDto>> placeList =
                PlaceIntersectionUtils.createExtendedPlacePalindromeDtoMap(allPlaceDtoListNew);

        List<PlacePalindromeCreationDto> resultWithoutLineCollision = placeList.stream()
                .map(this::createExtendedPlacePalindromeDto)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        Set<String> placeKeysBeforeMerge = getPlacesKeys(resultWithoutLineCollision);
        log.info("Type: {}. Places {} in polygons before merge", type, placeKeysBeforeMerge.size());
        List<PlacePalindromeCreationDto> allPalindrome = resultWithoutLineCollision
                .stream()
                .peek(places -> {
                    createPalindromeLines(places);
                    createPolygonPoints(places);
                })
                .peek(this::mergePalindrome)
                .filter(it -> !it.getPolygonPoints().isEmpty())
                .collect(Collectors.toMap(
                        places -> String.join(",", places.getPlaceKeys()),
                        places -> places,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream().toList();


        Set<String> placeKeys = getPlacesKeys(allPalindrome);
        log.info("Type: {}. Places {} in polygons after merge", type, placeKeys.size());

        List<PlacePalindromeCreationDto> result = allPalindrome.stream()
                .filter(palindrome -> isPolygonInsideAnyPolygon(palindrome, allPalindrome))
                .peek(this::findCenter)
                .toList();
        Set<String> placeKeysResult = getPlacesKeys(result);
        log.info("Type: {}. Places {} in polygons after delete inside", type, placeKeysResult.size());
        return result;
    }

    private boolean isPolygonInsideAnyPolygon(
            PlacePalindromeCreationDto palindrome, List<PlacePalindromeCreationDto> allPalindrome) {
        return allPalindrome.stream()
                .noneMatch(palindromeOutside -> PalindromeUtils.isPolygonInsidePolygon(
                        palindrome.getPolygonPoints(),
                        palindromeOutside.getPolygonPoints()
                ));
    }

    private static void createPalindromeLines(PlacePalindromeCreationDto places) {
        if (places.getPalindromeInformationForCreation().getPalindromeLines() == null) {
            return;
        }
        for (PolygonLineDto line : places.getPalindromeInformationForCreation().getPalindromeLines()) {
            List<PointDto> circleFirst = CircleCreatorPointUtils.createFirstCircleLines(line);
            List<PointDto> circleSecond = CircleCreatorPointUtils.createSecondCircleLines(line);

            List<PointDto> lineFirst = new ArrayList<>();
            if (!line.isTouchedExternalLineFirst) {
                lineFirst = LinesCreatorPointUtils.interpolatePoints(
                        line.getTangentExternalLineFirst().getFirstCirclePoint(),
                        line.getTangentExternalLineFirst().getSecondCircePoint()
                );
            } else {
                List<PointDto> internalLinePartOne1 = LinesCreatorPointUtils.interpolatePoints(
                        line.getTangentInternalLineFirst().getFirstCirclePoint(), line.tangentInternalLinesConnectionPoint);
                List<PointDto> internalLinePartOne2 = LinesCreatorPointUtils.interpolatePoints(
                        line.tangentInternalLinesConnectionPoint, line.getTangentInternalLineSecond().getSecondCircePoint()
                );
                lineFirst.addAll(internalLinePartOne1);
                lineFirst.addAll(internalLinePartOne2);
            }

            List<PointDto> lineSecond;
            if (!line.isTouchedExternalLineSecond) {
                lineSecond = LinesCreatorPointUtils.interpolatePoints(
                        line.getTangentExternalLineSecond().getSecondCircePoint(),
                        line.getTangentExternalLineSecond().getFirstCirclePoint()
                );
            } else {
                lineSecond = new ArrayList<>();
                List<PointDto> internalLinePartOne1 = LinesCreatorPointUtils.interpolatePoints(
                        line.getTangentInternalLineFirst().getSecondCircePoint(), line.tangentInternalLinesConnectionPoint);
                List<PointDto> internalLinePartOne2 = LinesCreatorPointUtils.interpolatePoints(
                        line.tangentInternalLinesConnectionPoint, line.getTangentInternalLineSecond().getFirstCirclePoint()
                );

                lineSecond.addAll(internalLinePartOne1);
                lineSecond.addAll(internalLinePartOne2);
            }

            PolygonLineWithPointsDto polygonLineWithPointsDto = PolygonLineWithPointsDto.builder()
                    .circleFirst(circleFirst)
                    .lineFirst(lineFirst)
                    .circleSecond(circleSecond)
                    .lineSecond(lineSecond)
                    .build();

            line.setPalindromeLineWithPointsList(polygonLineWithPointsDto);
        }
    }

    private void mergePalindrome(PlacePalindromeCreationDto places) {
        //create list of polygons before merge
        Set<PolygonLineDto> palindromeLineSet = places.getPalindromeInformationForCreation().getPalindromeLines();
        List<List<PointDto>> polygonPointsBeforeMerge = palindromeLineSet.stream()
                .map(PolygonLineDto::getPolygon)
                .collect(Collectors.toList());
        places.setPolygonPointsBeforeMerge(polygonPointsBeforeMerge);

        //merge polygons
        List<PointDto> polygon = PalindromeUtils.merge(palindromeLineSet);
        places.setPolygonPoints(polygon);

        //delete not near points
        deleteNotNearPoints(polygon);

        places.setPalindromeInformationForCreation(null);
    }

    private void findCenter(PlacePalindromeCreationDto places) {
        PointDto centerPoint = PalindromeUtils.findPoleOfInaccessibilityFromLib(places.getPolygonPoints());

        places.setPolygonCenter(centerPoint);
    }

    private void createPolygonPoints(
            PlacePalindromeCreationDto places) {
        if (places.getPlaceKeys().size() == 1) {
            CirclePolygonDto circleDto = places.getPalindromeInformationForCreation().getCirclesDimension().stream().toList().get(0);
            List<PointDto> circle = CircleCreatorPointUtils.creteFullCircle(
                    circleDto.center, circleDto.radios
            );

            PolygonLineDto polygonLineDto = PolygonLineDto.builder()
                    .polygon(circle)
                    .build();
            if (places.getPalindromeInformationForCreation().getPalindromeLines() == null) {
                places.getPalindromeInformationForCreation().setPalindromeLines(Set.of(polygonLineDto));
            } else {
                places.getPalindromeInformationForCreation().getPalindromeLines().add(polygonLineDto);
            }
        } else {
            for (PolygonLineDto polygonLineDto : places.getPalindromeInformationForCreation().getPalindromeLines()) {
                List<PointDto> commonLines = new ArrayList<>();

                if (polygonLineDto.getPalindromeLineWithPointsList().circleFirst.isEmpty() ||
                        polygonLineDto.getPalindromeLineWithPointsList().lineFirst.isEmpty() ||
                        polygonLineDto.getPalindromeLineWithPointsList().circleSecond.isEmpty() ||
                        polygonLineDto.getPalindromeLineWithPointsList().lineSecond.isEmpty()
                ) {
                    continue;
                }
                commonLines.addAll(
                        polygonLineDto.getPalindromeLineWithPointsList().circleFirst
                );
                commonLines.addAll(
                        polygonLineDto.getPalindromeLineWithPointsList().lineFirst
                );
                commonLines.addAll(
                        polygonLineDto.getPalindromeLineWithPointsList().circleSecond
                );
                commonLines.addAll(
                        polygonLineDto.getPalindromeLineWithPointsList().lineSecond
                );

                commonLines.add(polygonLineDto.getPalindromeLineWithPointsList().circleFirst.get(0));

                polygonLineDto.setPolygon(commonLines);
            }
        }
    }

    private Optional<PlacePalindromeCreationDto> createExtendedPlacePalindromeDto(
            List<PlaceForPalindromeCreationDto> placeDtoList
    ) {
        if (!placeDtoList.isEmpty()) {
            PlaceForPalindromeCreationDto firstPlace = placeDtoList.get(0);

            List<String> placeKeys = new ArrayList<>();
            Set<CirclePolygonDto> circles = new HashSet<>();
            Set<PolygonLineDto> palindromeLines = new HashSet<>();

            for (PlaceForPalindromeCreationDto placeDto :placeDtoList) {
                CirclePolygonDto circleDto = CirclePolygonDto.builder()
                        .radios(placeDto.getSize())
                        .center(placeDto.getCoordinates())
                        .build();
                circles.add(circleDto);
                placeKeys.add(placeDto.getPlaceKey());

                if (placeDto.getTangentPoints() != null) {
                    Set<PolygonLineDto> palindromeLinesOnePlace = getPalindromeLineDtoForPlace(placeDto);
                    palindromeLines.addAll(palindromeLinesOnePlace);
                }
            }

            PolygonsDto palindromeDto =  PolygonsDto.builder()
                    .circlesDimension(circles)
                    .palindromeLines(palindromeLines)
                    .build();

            PlacePalindromeCreationDto resultPlace = PlacePalindromeCreationDto.builder()
                    .dominantGroupKey(firstPlace.getDominantGroupKey())
                    .dominantGroup(firstPlace.getDominantGroup())
                    .palindromeInformationForCreation(palindromeDto)
                    .placeKeys(placeKeys)
                    .build();

            return Optional.of(resultPlace);
        } else {
            return Optional.empty();
        }
    }

    private Set<PolygonLineDto> getPalindromeLineDtoForPlace(
            PlaceForPalindromeCreationDto placeDto) {
        if (placeDto.getConnectingPlacesByPair().isEmpty()) {
            return Set.of();
        } else {
            List<Set<String>> connectingPlaces = placeDto.getConnectingPlacesByPair();

            return placeDto.getTangentPoints().stream()
                    .filter(it -> isPalindromeLineDto(connectingPlaces, it))
                    .collect(Collectors.toSet());
        }
    }

    private boolean isPalindromeLineDto(
            List<Set<String>> connectingPlaces, PolygonLineDto polygonLineDto
    ) {
        Set<String> tangentPointPlaces = new HashSet<>();
        tangentPointPlaces.add(polygonLineDto.getPlaceKeyFirst());
        tangentPointPlaces.add(polygonLineDto.getPlaceKeySecond());

        return connectingPlaces.contains(tangentPointPlaces);
    }

    private void deleteNotNearPoints(List<PointDto> points) {
        for (int i = 0; i < points.size() - 1; i++) {
            double distance = PointCommonUtils.calculateDistanceBetweenPoints(
                    points.get(i), points.get(i + 1)
            );
            if (distance > PointCommonUtils.MIN_DISTANCE_BETWEEN_POINTS) {
                points.set(i + 1, points.get(i));
            }
        }
    }

    private Set<String> getPlacesKeys(List<PlacePalindromeCreationDto> allPalindrome) {
        Set<String> keys = new HashSet<>();
        for (PlacePalindromeCreationDto palindrome : allPalindrome) {
            keys.addAll(palindrome.getPlaceKeys());
        }

        return keys;
    }
}
