package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hibernate.validator.constraints.Range;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.mappers.GroupMapper;
import to.orbis.v2.backend.mappers.PlaceMapper;
import to.orbis.v2.backend.mappers.PointMapper;
import to.orbis.v2.backend.mappers.PostMapper;
import to.orbis.v2.backend.models.dto.*;
import to.orbis.v2.backend.models.requests.places.CreatePlaceRequest;
import to.orbis.v2.backend.models.requests.places.RatePlaceRequest;
import to.orbis.v2.backend.models.requests.places.UpdatePlaceRequest;
import to.orbis.v2.backend.services.FollowsService;
import to.orbis.v2.backend.services.GroupsService;
import to.orbis.v2.backend.services.PlacesService;
import to.orbis.v2.backend.services.PostService;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import to.orbis.v2.backend.exceptions.ForwardToNodeJsException;
import to.orbis.v2.backend.utils.GeoHashUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

import static to.orbis.v2.backend.utils.ControllerUtils.maybeAuthorized;

@Slf4j
@RestController
@RequestMapping(value = "/places", produces = "application/json")
@RequiredArgsConstructor
public class PlacesController {

    PlacesService placesService;
    GroupsService groupsService;
    PlaceMapper placeMapper;
    PointMapper pointMapper;
    PostMapper postMapper;
    PostService postService;
    FollowsService followsService;
    GroupMapper groupMapper;
    ReactiveMongoTemplate mongoTemplate;
    to.orbis.v2.backend.services.NetworkEventLookupService networkEventLookupService;

    @GetMapping
    @PreAuthorize("permitAll")
    public Mono<List<ExtendedPlaceDto>> findPlaces(
            @RequestParam(required = false) @Validated @Range(min = -90, max = 90) Double latitude,
            @RequestParam(required = false) @Validated @Range(min = -180, max = 180) Double longitude,
            @RequestParam(required = false, defaultValue = "10.0") Double distance,
            @RequestParam(required = false) Optional<String> name,
            @RequestParam(required = false) Optional<String> ownedByGroupKey,
            @RequestParam(required = false) Optional<String> ownedByGroupSlug,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication authentication) {
        log.info("Incoming request: /places (javaProxied={})", javaProxied);
        log.info("findPlaces: latitude={} longitude={} distance={} page={} size={} ownedByGroupKey={}", latitude,
                longitude, distance, page, size, ownedByGroupKey.orElse("none"));
        val location = Optional.ofNullable(longitude)
                .flatMap(lon -> Optional.ofNullable(latitude)
                        .map(lat -> new GeoJsonPoint(lon, lat)));

        val auth = getAuthenticatedUserKey(authentication);

        return Mono.defer(() -> {
            if (ownedByGroupKey.isPresent()) {
                return getNetworkEventIdByKey(ownedByGroupKey.get(), "places", javaProxied);
            } else if (latitude != null && longitude != null) {
                return getNetworkEventId(latitude, longitude, "places", javaProxied);
            }
            return Mono.empty();
        })
                .flatMap(eventId -> Mono.<List<ExtendedPlaceDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(Mono.defer(() -> ownedByGroupSlug
                        .map(s -> groupsService.getGroupBySlug(s)
                                .map(group -> Optional.of(group.getGroupKey())))
                        .orElseGet(() -> Mono.just(ownedByGroupKey))
                        .flatMap(group -> placesService.findPlaces(
                                location,
                                distance,
                                name,
                                group,
                                auth,
                                PageRequest.of(page, size))
                                .map(placeMapper::extendedPlaceToExtendedPlaceDto)
                                .buffer()
                                .filter(list -> !list.isEmpty())
                                .singleOrEmpty()
                                .switchIfEmpty(fillFromGooglePlaces(location, name, page, size)))
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException()))));
    }

    private Mono<List<ExtendedPlaceDto>> fillFromGooglePlaces(Optional<GeoJsonPoint> location, Optional<String> name,
            int page, int size) {
        // never look in google places further then 1st page or if location is unknown
        if (page != 0 || location.isEmpty()) {
            return Mono.empty();
        }

        return placesService.fillFromGooglePlaces(location, name, size)
                .map(placeMapper::placeToExtendedPlace)
                .map(placeMapper::extendedPlaceToExtendedPlaceDto)
                .buffer()
                .filter(list -> !list.isEmpty())
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @PutMapping("/{placeKey}/share")
    @PreAuthorize("permitAll")
    public Mono<String> sharePlace(@PathVariable String placeKey) {
        return placesService.sharePlace(placeKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Place with this key is not found")));
    }

    @PostMapping("/{placeKey}/report")
    public Mono<String> reportPlace(@PathVariable String placeKey, @RequestBody(required = false) String reason,
            Authentication authentication) {

        return placesService
                .reportPlace(placeKey, Optional.ofNullable(reason).orElse("not specified"),
                        maybeAuthorized(authentication))
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Place with this key is not found")));
    }

    @GetMapping("/slug/{slug}/events")
    @PreAuthorize("permitAll")
    public Mono<List<PostDto>> eventsBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "false") boolean pastEvents,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication auth) {
        return placesService.getPlaceBySlug(slug)
                .flatMap(place -> events(place.getPlaceKey(), pastEvents, page, size, auth));
    }

    @GetMapping("/{placeKey}/events")
    @PreAuthorize("permitAll")
    public Mono<List<PostDto>> events(
            @PathVariable String placeKey,
            @RequestParam(required = false, defaultValue = "false") boolean pastEvents,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication auth) {
        return postService.getPlaceEvents(placeKey, pastEvents, page, size, maybeAuthorized(auth))
                .map(postMapper::extendedPostToPostDto)
                .buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @GetMapping("/map")
    @PreAuthorize("permitAll")
    public Mono<List<ExtendedPlaceDto>> findPlacesForMap(
            @Validated @Range(min = -90, max = 90) double latitude,
            @Validated @Range(min = -180, max = 180) double longitude,
            @RequestParam(required = false, defaultValue = "10.0") Double distance,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            Authentication authentication) {
        val auth = getAuthenticatedUserKey(authentication);
        return placesService.findPlacesDtoForMap(
                new GeoJsonPoint(longitude, latitude),
                distance,
                auth,
                PageRequest.of(page, size))
                .buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @GetMapping("/slug/{slug}")
    public Mono<ExtendedPlaceDto> getPlaceBySlug(@PathVariable String slug, Authentication authentication) {
        return placesService.getPlaceBySlug(slug)
                .flatMap(place -> getPlace(place.getPlaceKey(), authentication));
    }

    @GetMapping("/{placeKey}")
    @PreAuthorize("permitAll")
    public Mono<ExtendedPlaceDto> getPlace(@PathVariable String placeKey, Authentication authentication) {

        val auth = getAuthenticatedUserKey(authentication);

        return placesService.getPlace(placeKey, auth)
                .map(placeMapper::extendedPlaceToExtendedPlaceDto)
                .switchIfEmpty(Mono.error(new NoDataFoundException("Place not found")));

    }

    private Optional<String> getAuthenticatedUserKey(Authentication authentication) {
        return Optional.of(authentication)
                .filter(a -> !(a instanceof AnonymousAuthenticationToken)).map(Principal::getName);
    }

    @PostMapping(consumes = "application/json")
    @Operation(operationId = "createPlace", security = @SecurityRequirement(name = "firebase"))
    @PreAuthorize("isAuthenticated")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PlaceDto> createPlace(@Validated @RequestBody CreatePlaceRequest place, Authentication authentication) {
        return placesService.createPlace(
                placeMapper.cratePlaceRequestToPlace(place).setUserCreatedKey(authentication.getName()),
                pointMapper.pointToGeiGeoJsonPoint(place.getUserCoordinates()))
                .flatMap(savedPlace -> this.getPlace(savedPlace.getPlaceKey(), authentication));
    }

    @PutMapping(path = "/{placeKey}", consumes = "application/json")
    @Operation(operationId = "updatePlace", security = @SecurityRequirement(name = "firebase"))
    @PreAuthorize("isAuthenticated")
    public Mono<PlaceDto> updatePlace(
            @PathVariable String placeKey,
            @Validated @RequestBody UpdatePlaceRequest place,
            Authentication authentication) {
        return placesService.updatePlace(placeKey,
                placeMapper.updatePlaceRequestToPlace(place)
                        .setUserCreatedKey(authentication.getName()))
                .flatMap(up -> this.getPlace(up.getPlaceKey(), authentication));
    }

    @PutMapping("/{placeKey}/follow")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "followPlace", security = @SecurityRequirement(name = "firebase"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> followPlace(@PathVariable String placeKey, Authentication principal) {
        return followsService.followPlace(placeKey, principal.getName());
    }

    @DeleteMapping("/{placeKey}/follow")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "unfollowPlace", security = @SecurityRequirement(name = "firebase"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unfollowPlace(@PathVariable String placeKey, Authentication principal) {
        return followsService.unfollowPlace(placeKey, principal.getName());
    }

    @GetMapping(value = "/slug/{slug}/followers")
    public Mono<List<SimplifiedUserDto>> followersBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size) {

        return placesService.getPlaceBySlug(slug)
                .flatMap(place -> followers(place.getPlaceKey(), page, size));
    }

    @GetMapping(value = "/{placeKey}/followers")
    public Mono<List<SimplifiedUserDto>> followers(
            @PathVariable String placeKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size) {

        return followsService.getPlaceFollowers(
                placeKey,
                PageRequest.of(page, size))
                .map(groupMapper::simplifiedUserToSimplifiedUserDto)
                .buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @PostMapping(value = "/rate", consumes = "application/json")
    @Operation(operationId = "createPlace", security = @SecurityRequirement(name = "firebase"))
    @PreAuthorize("isAuthenticated")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PlaceRateResult> ratePlace(
            @Validated @RequestBody RatePlaceRequest request, Authentication principal) {
        return placesService.ratePlace(request, principal.getName());
    }

    private Mono<String> getNetworkEventId(Double latitude, Double longitude, String collectionName,
            boolean javaProxied) {
        return networkEventLookupService.byGeo(collectionName, latitude, longitude, javaProxied);
    }

    private Mono<String> getNetworkEventIdByKey(String key, String collectionName, boolean javaProxied) {
        return networkEventLookupService.byParent(collectionName, key, javaProxied);
    }

    private String hashShort(String value) {
        if (value == null || value.isEmpty()) {
            return "00000000";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
