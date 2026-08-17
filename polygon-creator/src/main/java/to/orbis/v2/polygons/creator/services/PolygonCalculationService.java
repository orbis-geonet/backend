package to.orbis.v2.polygons.creator.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import to.orbis.v2.polygons.creator.mappers.PolygonMapper;
import to.orbis.v2.polygons.creator.models.constant.PolygonSchedulerCoordinateType;
import to.orbis.v2.polygons.creator.models.dto.PalindromeCreationResult;
import to.orbis.v2.polygons.creator.models.entity.PolygonSchedulerCoordinate;
import to.orbis.v2.polygons.creator.models.dto.PlacePalindromeCreationDto;
import to.orbis.v2.polygons.creator.models.entity.Polygon;
import to.orbis.v2.polygons.creator.repositories.PolygonAggregationRepository;
import to.orbis.v2.polygons.creator.repositories.PolygonRepository;
import to.orbis.v2.polygons.creator.utils.MemoryUtils;
import to.orbis.v2.polygons.creator.utils.PolygonCalculationUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PolygonCalculationService {

    private final PolygonRepository polygonRepository;
    private final PolygonAggregationRepository polygonAggregationRepository;
    private final PolygonMapper polygonMapper;
    private final PlacesService placesService;

    public PalindromeCreationResult processCoordinate(
            PolygonSchedulerCoordinate coordinate, PolygonSchedulerCoordinateType type) {
        double longitude = coordinate.getLongitude();
        double latitude = coordinate.getLatitude();
        double radius = coordinate.getRadius();

        log.info("Type: {}. key: {}. Calculating polygons for coordinate: ({}, {}), radius: {}", type, coordinate.getPolygonSchedulerCoordinateKey(), longitude, latitude, radius);
        MemoryUtils.printCurrentMemoryInfo("Type: %s. processCoordinate: BEFORE polygons calculation".formatted(type));

        Instant coordinateStart = Instant.now();

        PalindromeCreationResult palindromeCreationResult = placesService.findPlacesAndCreatePalindrome(
                new GeoJsonPoint(longitude, latitude), radius, type
        );

        List<PlacePalindromeCreationDto> result = palindromeCreationResult.getPalindromes();

        Duration duration = Duration.between(coordinateStart, Instant.now());
        log.info("Type: {}. key: {}. Time taken for coordinate ({}, {}) {} milliseconds",
                type, coordinate.getPolygonSchedulerCoordinateKey(), coordinate.getLongitude(), coordinate.getLatitude(), duration.toMillis());

        List<Polygon> newPolygons = result.stream()
                .map(polygonMapper::placePalindromeDtoToPolygon)
                .toList();

        List<Polygon> edgePolygons = newPolygons.stream()
                .filter(existingPolygon -> {
                    double distanceFromCenter = calculateDistance(
                            existingPolygon.getPolygonCenter().getLatitude(),
                            existingPolygon.getPolygonCenter().getLongitude(),
                            coordinate.getLatitude(),
                            coordinate.getLongitude()
                    );
                    // it's an edge polygon
                    return distanceFromCenter > coordinate.getRadius() * 0.75;
                }).toList();

        List<Polygon> existingPolygons = polygonRepository.findByPolygonCenterWithinRadius(
                coordinate.getLongitude(), coordinate.getLatitude(), coordinate.getRadius() / PolygonCalculationUtils.EARTH_RADIUS_KM
        );

        List<Polygon> polygonsToDelete = existingPolygons.stream()
                .filter(existingPolygon -> edgePolygons.stream()
                        .noneMatch(edgePolygon -> polygonKeysIntersect(existingPolygon, edgePolygon))
                )
//                .map(Polygon::getPolygonKey)
                .collect(Collectors.toList());

        // Determine which new polygons to save
        List<Polygon> polygonsToSave = newPolygons.stream()
                .filter(newPolygon -> edgePolygons.stream()
                        .noneMatch(edgePolygon -> polygonKeysIntersect(newPolygon, edgePolygon)))
                .collect(Collectors.toList());

        // Delete the determined polygons
        polygonRepository.deleteAll(polygonsToDelete);
//        polygonAggregationRepository.deleteByKeys(polygonsToDelete);

        polygonAggregationRepository.saveAll(polygonsToSave);

        log.info("Type: {}. key: {}. Completed processing for coordinate: ({}, {}). radius: {}", type, coordinate.getPolygonSchedulerCoordinateKey(), longitude, latitude, radius);
        MemoryUtils.printCurrentMemoryInfo("Type: %s. processCoordinate: AFTER polygons calculation".formatted(type));

        return palindromeCreationResult;
    }


    /**
     * Checks if two polygons intersact - meaning they share the same key.
     */
    private boolean polygonKeysIntersect(Polygon p1, Polygon p2) {
        List<String> placeKeys1 = p1.getPlaceKeys();
        List<String> placeKeys2 = p2.getPlaceKeys();

        return placeKeys1.stream().anyMatch(placeKeys2::contains);
    }

    /**
     * Calculates distance in km for 2 coordinates
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // convert to distance in km
    }
}
