package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Range;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.validation.annotation.Validated;
import to.orbis.v2.backend.exceptions.ForwardToNodeJsException;
import to.orbis.v2.backend.utils.GeoHashUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.PolygonSchedulerCoordinateStatus;
import to.orbis.v2.backend.models.dto.PlacePalindromeCreationDto;
import to.orbis.v2.backend.models.dto.PlacePalindromeDto;
import to.orbis.v2.backend.models.dto.PolygonSchedulerCoordinateStatusDto;
import to.orbis.v2.backend.services.PolygonSchedulerService;
import to.orbis.v2.backend.services.PolygonCalculationService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/polygon-calculations", produces = "application/json")
@RequiredArgsConstructor
public class PolygonCalculationController {

    PolygonCalculationService polygonCalculationService;
    PolygonSchedulerService polygonSchedulerService;
    ReactiveMongoTemplate mongoTemplate;

    @GetMapping("/polygons-page")
    @PreAuthorize("permitAll")
    public Mono<List<PlacePalindromeDto>> findPolygons(
            @Validated @Range(min = -90, max = 90) double latitude,
            @Validated @Range(min = -180, max = 180) double longitude,
            @RequestParam(required = false, defaultValue = "10.0") Double distance,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {
        log.info("Incoming request: /polygon-calculations/polygons-page");
        return getNetworkEventId(latitude, longitude, "polygons", javaProxied)
                .flatMap(eventId -> Mono.<List<PlacePalindromeDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(polygonCalculationService.findPolygonsPageable(
                        new GeoJsonPoint(longitude, latitude),
                        distance,
                        PageRequest.of(page, size))
                        .collectList()
                        .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list)))
                .switchIfEmpty(Mono.error(new ForwardToNodeJsException()));
    }

    @GetMapping("/polygons")
    @PreAuthorize("permitAll")
    public Mono<List<PlacePalindromeDto>> findPolygons(
            @Validated @Range(min = -90, max = 90) double latitude,
            @Validated @Range(min = -180, max = 180) double longitude,
            @RequestParam(required = false) String groupKey,
            @RequestParam(required = false, defaultValue = "10.0") Double distance,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {
        log.info("Incoming request: /polygon-calculations/polygons");
        return getNetworkEventId(latitude, longitude, "polygons", javaProxied)
                .flatMap(eventId -> Mono.<List<PlacePalindromeDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(polygonCalculationService.findPolygons(latitude, longitude, groupKey, distance));
    }

    @GetMapping("/polygons/{placeKey}")
    @PreAuthorize("permitAll")
    public Mono<PlacePalindromeDto> findPolygonByPlaceKey(@PathVariable String placeKey) {
        return polygonCalculationService.findByPlaceKey(placeKey);
    }

    /**
     * This is an endpoint that is created for the Initial Polygon calculation - so
     * it should be called only once,
     * when the new Polygon calculation logic changes are deployed.
     */
    @GetMapping("/trigger")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "trigger", security = @SecurityRequirement(name = "firebase"))
    public Mono<String> trigger() {
        return polygonSchedulerService.trigger();
    }

    @GetMapping("/trigger-one-point")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "triggerOnePoint", security = @SecurityRequirement(name = "firebase"))
    public Mono<String> triggerOnePoint(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return polygonSchedulerService.addPolygonSchedulerForOnePoint(latitude, longitude);
    }

    @GetMapping("/{key}")
    public Mono<PolygonSchedulerCoordinateStatusDto> getStatusPolygonCalculation(
            @PathVariable String key) {
        return polygonSchedulerService.getStatusPolygonCalculation(key);
    }

    private Mono<String> getNetworkEventId(Double latitude, Double longitude, String collectionName,
            boolean javaProxied) {
        if (javaProxied || latitude == null || longitude == null) {
            return Mono.empty();
        }
        String geohash = GeoHashUtils.geoHashEncode3Bytes(latitude, longitude);
        log.info("Hash produced: {}", geohash);

        Query query = Query.query(Criteria.where("collectionName").is(collectionName).and("status")
                .is("pending"))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        log.info("Query: {}", query);

        return mongoTemplate.find(query, org.bson.Document.class, "network_events")
                .filter(doc -> {
                    String docGeohash = doc.getString("geohash");
                    return geohash.equals(docGeohash);
                })
                .next()
                .map(doc -> {
                    String id = doc.getObjectId("_id").toHexString();
                    log.info("Found: id={}", id);
                    return id;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Not found for hash: {}", geohash);
                    return Mono.empty();
                }));
    }
}
