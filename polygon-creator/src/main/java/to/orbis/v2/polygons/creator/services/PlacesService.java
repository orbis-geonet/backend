package to.orbis.v2.polygons.creator.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import to.orbis.v2.polygons.creator.mappers.PlaceMapper;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;
import to.orbis.v2.polygons.creator.models.dto.PalindromeCreationResult;
import to.orbis.v2.polygons.creator.models.dto.PlaceForPalindromeCreationDto;
import to.orbis.v2.polygons.creator.models.dto.PlacePalindromeCreationDto;
import to.orbis.v2.polygons.creator.models.dto.PointDto;
import to.orbis.v2.polygons.creator.models.dto.SimplifiedGroupDto;
import to.orbis.v2.polygons.creator.models.entity.ExtendedPlace;
import to.orbis.v2.polygons.creator.models.entity.Polygon;
import to.orbis.v2.polygons.creator.repositories.PlacesAggregationsRepository;
import to.orbis.v2.polygons.creator.utils.points.CircleCalculationPointUtils;
import to.orbis.v2.polygons.creator.utils.points.PointCommonUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static to.orbis.v2.polygons.creator.utils.points.points.creator.PalindromeUtils.isPointInsidePolygon;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacesService {
    PolygonService polygonService;
    PlacesAggregationsRepository placesAggregationsRepository;
    PlaceMapper placeMapper;
    PlacesPalindromeService placesPalindromeService;

    public List<PlacePalindromeCreationDto> findPlacesForMapPalindromeWithGroups(
            GeoJsonPoint point, Double distance
    ) {
        Map<String, String> groupColour = new HashMap<>();
        return findPlacesAndCreatePalindrome(point, distance, PolygonSchedulerCoordinateType.TRIGGER).getPalindromes()
                .stream()
                .peek(palindrome -> addDefaultGroupToPolygon(palindrome, groupColour))
                .toList();
    }

    public List<PlacePalindromeCreationDto> getPolygonsFromDb(
            GeoJsonPoint point, Double distance
    ) {
        Map<String, String> groupColour = new HashMap<>();
        List<Polygon> polygons = polygonService.getPolygons(point.getX(), point.getY(), distance);
        return polygons.stream()
                .map(this::toPlacePalindromeCreationDto)
                .peek(palindrome -> addDefaultGroupToPolygon(palindrome, groupColour))
                .toList();
    }

    private PlacePalindromeCreationDto toPlacePalindromeCreationDto(Polygon polygon) {
        return PlacePalindromeCreationDto.builder()
                .dominantGroupKey(polygon.getGroupKey())
                .polygonPoints(polygon.getPolygonPoints())
                .polygonCenter(polygon.getPolygonCenter())
                .placeKeys(polygon.getPlaceKeys())
                .build();
    }

    private void addDefaultGroupToPolygon(PlacePalindromeCreationDto palindrome, Map<String, String> groupColour) {
        SimplifiedGroupDto group = new SimplifiedGroupDto();
        group.setGroupKey(palindrome.getDominantGroupKey());
        String colour = groupColour.getOrDefault(palindrome.getDominantGroupKey(), getRandomColour());
        groupColour.put(palindrome.getDominantGroupKey(), colour);
        group.setSolidColorHex(colour);
        palindrome.setDominantGroup(group);
    }

    public PalindromeCreationResult findPlacesAndCreatePalindrome(
            GeoJsonPoint point, Double distance, PolygonSchedulerCoordinateType type) {
        Instant startAt = Instant.now();

        Pageable pageable = PageRequest.of(0, 50000);
        List<PlaceForPalindromeCreationDto> place = findPlacesForMap(point, distance, pageable)
                .stream()
//                .filter(it -> it.getDominantGroupKey().equals("675f299e0270b24d125e5a7f"))
                .map(placeMapper::extendedPlaceToExtendedPlaceDto)
                .map(placeMapper::toPlaceForPalindromeCreationDto)
                .toList();

        log.info("Type: {}. findPlacesAndCreatePalindrome: found places before resize {}", type, place.size());
        //Calculate radius
        CircleCalculationPointUtils.calculateResizedRadius(place);

        List<String> needToUpdateRadiusPlaces = new ArrayList<>();
        List<PlaceForPalindromeCreationDto> placesForPalindromes = new ArrayList<>();
        List<PlaceForPalindromeCreationDto> placesWithZeroRadius = new ArrayList<>();

        for (PlaceForPalindromeCreationDto placeForPalindrome : place) {
            if (placeForPalindrome.getSize() >= PointCommonUtils.MIN_RADIOS_TO_SHOW) {
                placesForPalindromes.add(placeForPalindrome);
            } else {
                placeForPalindrome.setSize(0);
                placesWithZeroRadius.add(placeForPalindrome);
                needToUpdateRadiusPlaces.add(placeForPalindrome.getPlaceKey());
            }
        }

        log.info("Type: {}. findPlacesAndCreatePalindrome: found places for palindrome {}. places with 0 radius {}", type, placesForPalindromes.size(), needToUpdateRadiusPlaces.size());

//        placesAggregationsRepository.updateSizeForPlaces(needToUpdateRadiusPlaces);

        List<PlacePalindromeCreationDto> palindromes = placesPalindromeService.createListWithPalindrome(placesForPalindromes, type);

        addZeroRadiusPlacesToPalindrome(palindromes, placesWithZeroRadius, type);

        Long duration = Duration.between(startAt, Instant.now()).get(ChronoUnit.SECONDS);
        log.info("Type: {}. findPlacesAndCreatePalindrome: created palindromes {}. Time: {} seconds", type, palindromes.size(), duration);
        return PalindromeCreationResult.builder()
                .palindromes(palindromes)
                .numberOfPlaces(place.size())
                .numberOfPolygons(palindromes.size())
                .build();
    }

    public List<ExtendedPlace> findPlacesForMap(
            GeoJsonPoint point, Double distance, Pageable pageable) {
        return placesAggregationsRepository.findByCoordinatesNear(point, distance, true, pageable);
    }

    private void addZeroRadiusPlacesToPalindrome(
            List<PlacePalindromeCreationDto> palindromes,
            List<PlaceForPalindromeCreationDto> placesWithZeroRadius,
            PolygonSchedulerCoordinateType type
    ) {
        palindromes
                .forEach(palindrome -> {
                    String groupKey = palindrome.getDominantGroupKey();

                    log.info("Type: {}. Palindrome for group {} has groups BEFORE adding with 0 radios {}", type, groupKey, palindrome.getPlaceKeys().size());
                    List<PointDto> palindromePoints = palindrome.getPolygonPoints();
                    List<PlaceForPalindromeCreationDto> groupPlaces = getGroupPlaces(placesWithZeroRadius, groupKey);
                    addPlacesToPalindrome(palindrome, palindromePoints, groupPlaces);
                    log.info("Type: {}. Palindrome for group {} has groups AFTER adding with 0 radios {}", type, groupKey, palindrome.getPlaceKeys().size());
                });
    }

    private void addPlacesToPalindrome(
            PlacePalindromeCreationDto palindrome,
            List<PointDto> palindromePoints,
            List<PlaceForPalindromeCreationDto> groupPlaces
    ) {
        groupPlaces.forEach(groupPlace -> {
            PointDto placeCoordinate = groupPlace.getCoordinates();
            boolean isPointInsidePolygon = isPointInsidePolygon(palindromePoints, placeCoordinate);
            if (isPointInsidePolygon) {
                palindrome.getPlaceKeys().add(groupPlace.getPlaceKey());
            }
        });
    }

    private List<PlaceForPalindromeCreationDto> getGroupPlaces(
            List<PlaceForPalindromeCreationDto> places,
            String groupKey
    ) {
        return places.stream()
                .filter(place -> place.getDominantGroupKey() != null)
                .filter(place -> place.getDominantGroupKey().equals(groupKey))
                .toList();
    }

    private String getRandomColour() {
        Random random = new Random();
        int red = random.nextInt(256);
        int green = random.nextInt(256);
        int blue = random.nextInt(256);
        return String.format("#%02X%02X%02X", red, green, blue);
    }
}
