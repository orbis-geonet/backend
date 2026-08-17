package to.orbis.v2.polygons.creator.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Range;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import to.orbis.v2.polygons.creator.models.dto.PlacePalindromeCreationDto;
import to.orbis.v2.polygons.creator.services.PlacesService;
import to.orbis.v2.polygons.creator.services.PolygonSchedulerService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/polygon-calculations")
@RequiredArgsConstructor
//@ConditionalOnProperty(name = "app.polygon-calculation.test-controller-enable", havingValue = "true")
public class PolygonCalculationController {
    PolygonSchedulerService polygonSchedulerService;
    PlacesService placesService;

    @GetMapping("/map-palindrome")
    public List<PlacePalindromeCreationDto> findPlacesForMapPalindrome(
            @Validated @Range(min = -90, max = 90) double latitude,
            @Validated @Range(min = -180, max = 180) double longitude,
            @RequestParam(required = false, defaultValue = "10.0") Double distance) {
        return placesService.findPlacesForMapPalindromeWithGroups(
                new GeoJsonPoint(longitude, latitude),
                distance
        );
    }

    @GetMapping("/map-palindrome-from-db")
    public List<PlacePalindromeCreationDto> findPlacesForMapPalindromeFromDb(
            @Validated @Range(min = -90, max = 90) double latitude,
            @Validated @Range(min = -180, max = 180) double longitude,
            @RequestParam(required = false, defaultValue = "10.0") Double distance) {
        return placesService.getPolygonsFromDb(
                new GeoJsonPoint(longitude, latitude),
                distance
        );
    }

    @PostMapping("/trigger")
    public String trigger() {
        return polygonSchedulerService.trigger();
    }

    @PostMapping("/trigger-one-point")
    public String triggerOnePoint(
            @RequestParam double latitude,
            @RequestParam double longitude
    ) {
        return polygonSchedulerService.addPolygonSchedulerForOnePoint(latitude, longitude);
    }
}
