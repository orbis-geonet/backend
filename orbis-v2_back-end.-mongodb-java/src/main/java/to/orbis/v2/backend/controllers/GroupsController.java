package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hibernate.validator.constraints.Range;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.ForwardToNodeJsException;
import to.orbis.v2.backend.utils.GeoHashUtils;
import to.orbis.v2.backend.filters.LocationWebFilter;
import to.orbis.v2.backend.mappers.GroupMapper;
import to.orbis.v2.backend.mappers.PostMapper;
import to.orbis.v2.backend.mappers.SubscriptionMapper;
import to.orbis.v2.backend.mappers.UserMapper;
import to.orbis.v2.backend.models.dto.*;
import to.orbis.v2.backend.models.entity.ExtendedGroup;
import to.orbis.v2.backend.models.entity.Group;
import to.orbis.v2.backend.models.entity.SimplifiedGroup;
import to.orbis.v2.backend.models.requests.groups.CreateGroupRequest;
import to.orbis.v2.backend.models.requests.groups.UpdateGroupRequest;
import to.orbis.v2.backend.repositories.queries.GroupQuery;
import to.orbis.v2.backend.services.FollowsService;
import to.orbis.v2.backend.services.GroupsService;
import to.orbis.v2.backend.services.PostService;
import to.orbis.v2.backend.services.SubscriptionsService;
import to.orbis.v2.backend.utils.ControllerUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.emptyToNull;
import static to.orbis.v2.backend.utils.ControllerUtils.maybeAuthorized;

@Slf4j
@Validated
@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupsController {
        GroupsService groupsService;
        PostService postService;
        FollowsService followsService;
        GroupMapper groupMapper;
        UserMapper userMapper;
        PostMapper postMapper;
        ReactiveMongoTemplate mongoTemplate;
        to.orbis.v2.backend.services.NetworkEventLookupService networkEventLookupService;

        @GetMapping
        public Mono<List<ExtendedGroupDto>> getGroups(
                        @RequestParam(required = false) @Validated @Range(min = -90, max = 90) Double latitude,
                        @RequestParam(required = false) @Validated @Range(min = -180, max = 180) Double longitude,
                        @RequestParam(required = false) Optional<Double> distance,
                        @RequestParam(required = false) String name,
                        @RequestParam(required = false, defaultValue = "false") boolean listMembers,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
                        @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
                        Authentication authentication) {

                log.info("getGroups: latitude={}, longitude={}, distance={}, name={}", latitude, longitude,
                                distance.isPresent() ? distance.get() : "empty", name);
                val groupQueryBuilder = GroupQuery.builder();

                val withLocation = Optional.ofNullable(longitude)
                                .flatMap(lon -> Optional.ofNullable(latitude)
                                                .map(lat -> new GeoJsonPoint(lon, lat)))
                                .map(groupQueryBuilder::closeTo)
                                .flatMap(wd -> distance.map(d -> wd.withDistance(new Distance(d, Metrics.KILOMETERS)))
                                                .or(() -> Optional.of(wd)))
                                .orElse(groupQueryBuilder);

                val withNameFilter = Optional.ofNullable(emptyToNull(name))
                                .map(groupQueryBuilder::withName)
                                .orElse(withLocation);

                val withAuth = (authentication.isAuthenticated()
                                && !(authentication instanceof AnonymousAuthenticationToken))
                                                ? withNameFilter.forUser(authentication.getName())
                                                : withNameFilter;

                val withPageable = withAuth.withPageable(PageRequest.of(page, size));

                val withListMembers = withPageable.listMembers(listMembers);

                val withUserLocation = locationHeader
                                .flatMap(lh -> Optional.ofNullable(LocationWebFilter.parseLocationHeader(lh)))
                                .map(withListMembers::userLocation)
                                .orElse(withListMembers);

                return getNetworkEventId(latitude, longitude, "groups", javaProxied)
                                .flatMap(eventId -> Mono
                                                .<List<ExtendedGroupDto>>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(groupsService.findGroups(withUserLocation.build())
                                                .map(groupMapper::extendedGroupToExtendedGroupDto)
                                                .collectList()
                                                .flatMap(list -> list.isEmpty() ? Mono.empty()
                                                                : Mono.just(list)))
                                .switchIfEmpty(Mono.error(new ForwardToNodeJsException()));
        }

        @GetMapping("/map")
        public Mono<List<String>> getGroupsForMap(
                        @RequestParam @Validated @Range(min = -90, max = 90) Double latitude,
                        @RequestParam @Validated @Range(min = -180, max = 180) Double longitude,
                        @RequestParam Double distance,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {
                Pageable pageable = PageRequest.of(page, size);
                return getNetworkEventId(latitude, longitude, "groups", javaProxied)
                                .flatMap(eventId -> Mono.<List<String>>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(groupsService
                                                .findGroupsForMap(latitude, longitude, distance,
                                                                PageRequest.of(page, size))
                                                .collectList()
                                                .flatMap(list -> list.isEmpty() ? Mono.empty()
                                                                : Mono.just(list)))
                                .switchIfEmpty(Mono.error(new ForwardToNodeJsException()));
        }

        @GetMapping("/recommended")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "findRecommendedGroups", security = @SecurityRequirement(name = "firebase"))
        public Mono<List<SimplifiedGroupDto>> findRecommendedGroups(
                        @RequestParam @Validated @Range(min = -90, max = 90) double latitude,
                        @RequestParam @Validated @Range(min = -180, max = 180) double longitude,
                        @RequestParam(required = false, defaultValue = "5") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
                        Authentication authentication) {
                return getNetworkEventId(latitude, longitude, "groups", javaProxied)
                                .flatMap(eventId -> Mono
                                                .<List<SimplifiedGroupDto>>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(groupsService
                                                .findRecommendedGroups(new GeoJsonPoint(longitude, latitude),
                                                                size,
                                                                authentication.getName())
                                                .map(list -> list.stream().map(
                                                                userMapper::simplifiedGroupToSimplifiedGroupDto)
                                                                .collect(Collectors.toList()))
                                                .singleOrEmpty()
                                                .flatMap(list -> list.isEmpty() ? Mono.empty()
                                                                : Mono.just(list)))
                                .switchIfEmpty(Mono.error(new ForwardToNodeJsException()));
        }

        @GetMapping("/rating")
        public Mono<List<SimplifiedGroupDto>> findRatedGroups(
                        @RequestParam @Validated @Range(min = -90, max = 90) double latitude,
                        @RequestParam @Validated @Range(min = -180, max = 180) double longitude,
                        @Parameter(description = "Time period in days for which rating will be built.") @RequestParam(required = false, defaultValue = "1") int timePeriod,
                        @RequestParam(required = false, defaultValue = "25") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {
                return getNetworkEventId(latitude, longitude, "groups", javaProxied)
                                .flatMap(eventId -> Mono
                                                .<List<SimplifiedGroupDto>>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(groupsService
                                                .findRatedGroups(new GeoJsonPoint(longitude, latitude),
                                                                timePeriod, size)
                                                .map(list -> list.stream().map(
                                                                userMapper::simplifiedGroupToSimplifiedGroupDto)
                                                                .collect(Collectors.toList()))
                                                .singleOrEmpty()
                                                .flatMap(list -> list.isEmpty() ? Mono.empty()
                                                                : Mono.just(list)))
                                .switchIfEmpty(Mono.error(new ForwardToNodeJsException()));
        }

        @GetMapping("/slug/{slug}")
        public Mono<ExtendedGroupDto> getGroupBySlug(
                        @PathVariable String slug,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
                        @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
                        Authentication authentication) {
                return groupsService.getGroupBySlug(slug)
                                .flatMap(group -> getGroup(group.getGroupKey(), javaProxied, locationHeader,
                                                authentication));
        }

        @GetMapping("/{groupKey}")
        public Mono<ExtendedGroupDto> getGroup(
                        @PathVariable String groupKey,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
                        @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
                        Authentication authentication) {
                log.info("Incoming request: /groups/{}", groupKey);

                val basicBuilder = GroupQuery.group(groupKey);

                val withAuth = (authentication.isAuthenticated()
                                && !(authentication instanceof AnonymousAuthenticationToken))
                                                ? basicBuilder.forUser(authentication.getName())
                                                : basicBuilder;

                val withUserLocation = locationHeader
                                .flatMap(lh -> Optional.ofNullable(LocationWebFilter.parseLocationHeader(lh)))
                                .map(withAuth::userLocation)
                                .orElse(withAuth);

                Flux<ExtendedGroup> serviceResponse = groupsService
                                .findGroups(withUserLocation.listMembers(true).build());

                return getNetworkEventIdByKey(groupKey, "groups", javaProxied)
                                .flatMap(eventId -> Mono.<ExtendedGroupDto>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(serviceResponse
                                                .map(groupMapper::extendedGroupToExtendedGroupDto)
                                                .singleOrEmpty()
                                                .switchIfEmpty(Mono.error(() -> new NoDataFoundException(
                                                                "Group with specified key is not found"))));
        }

        @GetMapping("/slug/{slug}/events")
        @PreAuthorize("permitAll")
        public Mono<List<PostDto>> eventsBySlug(
                        @PathVariable String slug,
                        @RequestParam(required = false, defaultValue = "false") boolean pastEvents,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
                        Authentication authentication) {
                return groupsService.getGroupBySlug(slug)
                                .flatMap(group -> events(group.getGroupKey(), pastEvents, page, size, javaProxied,
                                                authentication));
        }

        @GetMapping("/{groupKey}/events")
        @PreAuthorize("permitAll")
        public Mono<List<PostDto>> events(
                        @PathVariable String groupKey,
                        @RequestParam(required = false, defaultValue = "false") boolean pastEvents,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
                        Authentication authentication) {
                log.info("Incoming request: /groups/{}/events", groupKey);
                return getNetworkEventIdByKey(groupKey, "groups", javaProxied)
                                .flatMap(eventId -> Mono.<List<PostDto>>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(postService
                                                .getGroupEvents(groupKey, pastEvents, page, size,
                                                                ControllerUtils.maybeAuthorized(authentication))
                                                .map(postMapper::extendedPostToPostDto)
                                                .buffer()
                                                .singleOrEmpty()
                                                .switchIfEmpty(Mono
                                                                .error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException())));
        }

        @PutMapping("/{groupKey}")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "updateGroup", security = @SecurityRequirement(name = "firebase"))
        public Mono<ExtendedGroupDto> updateGroup(
                        @RequestBody @Validated UpdateGroupRequest updateRequest,
                        @PathVariable String groupKey,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
                        @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
                        Authentication authentication) {

                return groupsService.updateGroup(groupMapper.updateRequestToGroup(updateRequest).setGroupKey(groupKey),
                                authentication.getName())
                                .flatMap(ug -> this.getGroup(ug.getGroupKey(), javaProxied, locationHeader,
                                                authentication));
        }

        @DeleteMapping("/{groupKey}")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "deleteGroup", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> deleteGroup(@PathVariable String groupKey, Authentication authentication) {

                return groupsService.deleteGroup(groupKey, authentication.getName());
        }

        @PostMapping
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "createGroup", security = @SecurityRequirement(name = "firebase"))
        public Mono<ExtendedGroupDto> createGroup(@RequestBody @Validated CreateGroupRequest groupRequest,
                        Authentication authentication) {
                return groupsService
                                .createGroup(groupMapper.createRequestToGroup(groupRequest), authentication.getName())
                                .map(groupMapper::extendedGroupToExtendedGroupDto);
        }

        @PutMapping("/{groupKey}/share")
        @PreAuthorize("permitAll")
        public Mono<String> shareGroup(@PathVariable String groupKey) {
                return groupsService.shareGroup(groupKey)
                                .switchIfEmpty(Mono.error(
                                                () -> new NoDataFoundException("Group with this key is not found")));
        }

        @PostMapping("/{groupKey}/report")
        public Mono<String> reportGroup(@PathVariable String groupKey, @RequestBody(required = false) String reason,
                        Authentication authentication) {

                return groupsService
                                .reportGroup(groupKey, Optional.ofNullable(reason).orElse("not specified"),
                                                maybeAuthorized(authentication))
                                .switchIfEmpty(Mono.error(
                                                () -> new NoDataFoundException("Group with this key is not found")));
        }

        @PutMapping("/{groupKey}/members")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "addMember", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> addMember(@PathVariable String groupKey, Authentication authentication) {
                return groupsService.addMember(groupKey, authentication.getName());
        }

        @DeleteMapping(value = { "/{groupKey}/members", "/{groupKey}/members/{userKey}" })
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "removeMember", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> removeMember(@PathVariable String groupKey,
                        @PathVariable Optional<String> userKey,
                        Authentication authentication) {
                return groupsService.removeMember(groupKey, userKey.orElse(authentication.getName()),
                                authentication.getName());
        }

        @PutMapping("/{groupKey}/hideStories")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "hideStories", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> hideStories(@PathVariable String groupKey, Authentication authentication) {
                return groupsService.hideStories(groupKey, authentication.getName());
        }

        @DeleteMapping(value = { "/{groupKey}/hideStories" })
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "unhideStories", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> unhideStories(@PathVariable String groupKey,
                        Authentication authentication) {
                return groupsService.unhideStories(groupKey, authentication.getName());
        }

        @PutMapping("/{groupKey}/followers")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "addFollower", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> addFollower(@PathVariable String groupKey, Authentication authentication) {
                return groupsService.addFollower(groupKey, authentication.getName());
        }

        @DeleteMapping(value = { "/{groupKey}/followers", "/{groupKey}/followers/{userKey}" })
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "removeFollower", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> removeFollower(@PathVariable String groupKey,
                        @PathVariable Optional<String> userKey,
                        Authentication authentication) {
                return groupsService.removeFollower(groupKey, userKey.orElse(authentication.getName()),
                                authentication.getName());
        }

        @PutMapping("/{groupKey}/admins/{userKey}")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "addAdmin", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> addAdmin(@PathVariable String groupKey,
                        @PathVariable String userKey,
                        Authentication authentication) {
                return groupsService.addAdmin(groupKey, userKey, authentication.getName());
        }

        @DeleteMapping(value = { "/{groupKey}/admins/{userKey}", "/{groupKey}/admins" })
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "removeAdmin", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> removeAdmin(@PathVariable String groupKey,
                        @PathVariable Optional<String> userKey,
                        Authentication authentication) {
                return groupsService.removeAdmin(groupKey, userKey.orElse(authentication.getName()),
                                authentication.getName());
        }

        @GetMapping(value = "/slug/{slug}/followers")
        public Mono<List<SimplifiedUserDto>> followersBySlug(
                        @PathVariable String slug,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {

                return groupsService.getGroupBySlug(slug)
                                .flatMap(group -> followers(group.getGroupKey(), page, size, javaProxied));
        }

        @GetMapping(value = "/{groupKey}/followers")
        public Mono<List<SimplifiedUserDto>> followers(
                        @PathVariable String groupKey,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {
                log.info("Incoming request: /groups/{}/followers", groupKey);

                return getNetworkEventIdByKey(groupKey, "groups", javaProxied)
                                .flatMap(eventId -> Mono
                                                .<List<SimplifiedUserDto>>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(followsService.getGroupFollowers(
                                                groupKey,
                                                PageRequest.of(page, size))
                                                .map(groupMapper::simplifiedUserToSimplifiedUserDto)
                                                .buffer()
                                                .singleOrEmpty()
                                                .switchIfEmpty(Mono
                                                                .error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException())));
        }

        @GetMapping(value = "/slug/{slug}/members")
        public Mono<List<SimplifiedUserDto>> membersBySlug(
                        @PathVariable String slug,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {

                return groupsService.getGroupBySlug(slug)
                                .flatMap(group -> members(group.getGroupKey(), page, size, javaProxied));
        }

        @GetMapping(value = "/{groupKey}/members")
        public Mono<List<SimplifiedUserDto>> members(
                        @PathVariable String groupKey,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {
                log.info("Incoming request: /groups/{}/members", groupKey);

                return getNetworkEventIdByKey(groupKey, "groups", javaProxied)
                                .flatMap(eventId -> Mono
                                                .<List<SimplifiedUserDto>>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(groupsService.getGroupMembers(
                                                groupKey,
                                                PageRequest.of(page, size))
                                                .map(groupMapper::simplifiedUserToSimplifiedUserDto)
                                                .buffer()
                                                .singleOrEmpty()
                                                .switchIfEmpty(Mono
                                                                .error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException())));
        }

        @GetMapping(value = "/{groupKey}/admins")
        public Mono<List<SimplifiedUserDto>> admins(
                        @PathVariable String groupKey,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {
                log.info("Incoming request: /groups/{}/admins", groupKey);

                return getNetworkEventIdByKey(groupKey, "groups", javaProxied)
                                .flatMap(eventId -> Mono
                                                .<List<SimplifiedUserDto>>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(groupsService.getGroupAdmins(
                                                groupKey,
                                                PageRequest.of(page, size))
                                                .map(groupMapper::simplifiedUserToSimplifiedUserDto)
                                                .buffer()
                                                .singleOrEmpty()
                                                .switchIfEmpty(Mono
                                                                .error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException())));
        }

        @GetMapping(value = "/{groupKey}/banned")
        @PreAuthorize("isAuthenticated")
        public Mono<List<SimplifiedUserDto>> banned(
                        @PathVariable String groupKey,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "20") int size,
                        @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {

                return getNetworkEventIdByKey(groupKey, "groups", javaProxied)
                                .flatMap(eventId -> Mono
                                                .<List<SimplifiedUserDto>>error(new ForwardToNodeJsException(eventId)))
                                .switchIfEmpty(groupsService.getGroupBanned(
                                                groupKey,
                                                PageRequest.of(page, size))
                                                .map(groupMapper::simplifiedUserToSimplifiedUserDto)
                                                .buffer()
                                                .singleOrEmpty()
                                                .switchIfEmpty(Mono
                                                                .error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException())));
        }

        @DeleteMapping(value = "/{groupKey}/banned/{userKey}")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "removeBan", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> removeBan(@PathVariable String groupKey,
                        @PathVariable String userKey,
                        Authentication authentication) {
                return groupsService.removeBan(groupKey, userKey, authentication.getName());
        }

        @PutMapping(value = "/{groupKey}/banned/{userKey}")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "addBan", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> addBan(@PathVariable String groupKey,
                        @PathVariable String userKey,
                        Authentication authentication) {
                return groupsService.addBan(groupKey, userKey, authentication.getName());
        }

        @DeleteMapping(value = "/{groupKey}/places/{placeKey}")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "removePlaceFromGroup", security = @SecurityRequirement(name = "firebase"))
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> removePlaceFromGroup(@PathVariable String groupKey, @PathVariable String placeKey,
                        Authentication authentication) {
                return groupsService.removePlaceFromGroup(groupKey, placeKey, maybeAuthorized(authentication));
        }

        @PutMapping(value = "/{groupKey}/block")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "blockGroup", security = @SecurityRequirement(name = "firebase"))
        public Mono<Void> blockGroup(@PathVariable String groupKey, Authentication authentication) {
                log.info("blockGroup: groupKey={} userKey={}", groupKey, authentication.getName());
                return groupsService.blockGroup(groupKey, authentication.getName());
        }

        @DeleteMapping(value = "/{groupKey}/unblock")
        @PreAuthorize("isAuthenticated")
        @Operation(operationId = "unblockGroup", security = @SecurityRequirement(name = "firebase"))
        public Mono<Void> unblockGroup(@PathVariable String groupKey, Authentication authentication) {
                log.info("unblockGroup: groupKey={} userKey={}", groupKey, authentication.getName());
                return groupsService.unblockGroup(groupKey, authentication.getName());
        }

        private Mono<String> getNetworkEventIdByKey(String key, String collectionName, boolean javaProxied) {
                return networkEventLookupService.byKey(collectionName, key, javaProxied);
        }

        private Mono<String> getNetworkEventId(Double latitude, Double longitude, String collectionName,
                        boolean javaProxied) {
                return networkEventLookupService.byGeo(collectionName, latitude, longitude, javaProxied);
        }
}
