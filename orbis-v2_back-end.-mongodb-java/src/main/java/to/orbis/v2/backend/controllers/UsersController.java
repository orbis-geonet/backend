package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.UnknownUserException;
import to.orbis.v2.backend.exceptions.ForwardToNodeJsException;
import to.orbis.v2.backend.filters.LocationWebFilter;
import to.orbis.v2.backend.mappers.*;
import to.orbis.v2.backend.models.FollowType;
import to.orbis.v2.backend.models.dto.*;
import to.orbis.v2.backend.models.requests.CreateUserPicture;
import to.orbis.v2.backend.models.requests.users.UserDetailsRequest;
import to.orbis.v2.backend.services.FollowsService;
import to.orbis.v2.backend.services.UsersService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static to.orbis.v2.backend.utils.ControllerUtils.maybeAuthorized;

@Slf4j
@Validated
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class UsersController {
    UsersService usersService;
    FollowsService followsService;
    UserMapper userMapper;
    FollowsMapper followsMapper;
    GroupMapper groupMapper;
    UserPictureMapper userPictureMapper;
    ReactiveMongoTemplate mongoTemplate;

    @SneakyThrows
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "getMyDetails", security = @SecurityRequirement(name = "firebase"))
    public Mono<ExtendedUserDto> getMyDetails(
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {
        return getNetworkEventIdByKey(principal.getName(), "follows", javaProxied)
                .flatMap(eventId -> Mono.<ExtendedUserDto>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.getExtendedUser(principal.getName(), maybeAuthorized(principal))
                .switchIfEmpty(usersService
                        .defaultUserFromPrincipal((JwtAuthenticationToken) principal)
                        .map(userMapper::userToExtendedUser))
                .map(userMapper::extendedUserToExtendedUserDto));
    }

    @SneakyThrows
    @GetMapping
    public Mono<List<ExtendedUserDto>> findUsers(
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {
        return getNetworkEventIdByCollection("users", javaProxied)
                .flatMap(eventId -> Mono.<List<ExtendedUserDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.findExtendedUsers(name, maybeAuthorized(principal), PageRequest.of(page, size))
                        .map(userMapper::extendedUserToExtendedUserDtoSkipSensitiveFields)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @GetMapping("/slug/{slug}")
    public Mono<ExtendedUserDto> getUserBySlug(
            @PathVariable String slug,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication authentication) {
        return usersService.getUserBySlug(slug)
                .flatMap(user -> getUserDetails(user.getUserKey(), javaProxied, authentication));
    }

    @SneakyThrows
    @GetMapping("/{userKey}")
    public Mono<ExtendedUserDto> getUserDetails(
            @PathVariable String userKey,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {
        return getNetworkEventIdByKey(userKey, "users", javaProxied)
                .flatMap(eventId -> Mono.<ExtendedUserDto>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.getExtendedUser(userKey, maybeAuthorized(principal))
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("User with given ID is not found")))
                        .map(user -> userKey.equals(principal.getName())
                                ? userMapper.extendedUserToExtendedUserDto(user)
                                : userMapper.extendedUserToExtendedUserDtoSkipSensitiveFields(user)));
    }

    @GetMapping("/slug/{slug}/pictures")
    public Mono<List<UserPictureDto>> getUserPicturesBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal
    ) {
        return usersService.getUserBySlug(slug)
                .flatMap(user -> getUserPictures(Optional.of(user.getUserKey()), page, size, javaProxied, principal));
    }

    @GetMapping(path = {"/{userKey}/pictures", "/me/pictures"})
    public Mono<List<UserPictureDto>> getUserPictures(
            @PathVariable Optional<String> userKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal
    ) {
        String resolvedKey = userKey.orElse(principal.getName());
        return getNetworkEventIdByKey(resolvedKey, "userPicture", javaProxied)
                .flatMap(eventId -> Mono.<List<UserPictureDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.getUserPictures(resolvedKey, maybeAuthorized(principal), PageRequest.of(page, size))
                        .map(userPictureMapper::userPictureToUserPictureDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @PutMapping(path = {"/followers/{userKey}/accept"})
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "acceptFollower", security = @SecurityRequirement(name = "firebase"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> acceptFollower(@PathVariable String userKey,
                                     Authentication principal) {
        return followsService.acceptFollower(userKey, principal.getName())
                .then();

    }

    @DeleteMapping(path = {"/followers/{userKey}"})
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "declineFollowRequest", security = @SecurityRequirement(name = "firebase"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> declineFollower(@PathVariable String userKey,
                                      Authentication principal) {
        return followsService.declineFollower(userKey, principal.getName());

    }

    @GetMapping("/slug/{slug}/igpictures")
    public Mono<List<UserPictureDto>> getIgPicturesBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal
    ) {
        return usersService.getUserBySlug(slug)
                .flatMap(user -> getIgPictures(Optional.of(user.getUserKey()), page, size, javaProxied, principal));
    }

    @GetMapping(path = {"/{userKey}/igpictures", "/me/igpictures"})
    public Mono<List<UserPictureDto>> getIgPictures(
            @PathVariable Optional<String> userKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal
    ) {
        String resolvedKey = userKey.orElse(principal.getName());
        return getNetworkEventIdByKey(resolvedKey, "userPicture", javaProxied)
                .flatMap(eventId -> Mono.<List<UserPictureDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.getIgUserPictures(resolvedKey, maybeAuthorized(principal), PageRequest.of(page, size))
                        .map(userPictureMapper::userPictureToUserPictureDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));

    }

    @PostMapping(path = {"/me/igpictures/refresh"})
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "refreshIgPictures", security = @SecurityRequirement(name = "firebase"))
    public Mono<String> refreshIgPictures(Authentication principal) {
        return usersService.importInstagramPhotos(principal.getName()).buffer().singleOrEmpty().thenReturn("Refreshed");

    }

    @PostMapping(path = {"/me/pictures"})
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "addUserPicture", security = @SecurityRequirement(name = "firebase"))
    public Mono<UserPictureDto> addUserPicture(@RequestBody CreateUserPicture userPicture,
                                               Authentication principal) {
        return usersService.addUserPicture(userPictureMapper.createUserPictureToUserPicture(userPicture)
                        .setUserKey(principal.getName())
                        .setTimestamp(Instant.now()))
                .map(userPictureMapper::userPictureToUserPictureDto);

    }

    @DeleteMapping(path = {"/me/pictures/{pictureKey}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "getUserPictures", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> getUserPictures(@PathVariable String pictureKey) {
        return usersService.deleteUserPicture(pictureKey);

    }

    @SneakyThrows
    @PostMapping("/me")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "setMyDetails", security = @SecurityRequirement(name = "firebase"))
    public Mono<ExtendedUserDto> setMyDetails(@Validated @RequestBody UserDetailsRequest userDetailsRequest,
                                              Authentication principal) {
        return usersService.updateDetails(userMapper.userDetailsRequestToToUser(userDetailsRequest), (JwtAuthenticationToken) principal, userDetailsRequest)
                .map(userMapper::extendedUserToExtendedUserDto);
    }

    @PostMapping("/{userKey}/report")
    public Mono<String> reportUser(@PathVariable String userKey, @RequestBody(required = false) String reason,
                                   Authentication authentication) {

        return usersService.reportUser(userKey, Optional.ofNullable(reason).orElse("not specified"), maybeAuthorized(authentication))
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("User with this key is not found")));
    }

    @GetMapping("/{userKey}/share")
    public Mono<String> shareUser(@PathVariable String userKey) {

        return usersService.shareUser(userKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("User with this key is not found")));
    }

    @SneakyThrows
    @DeleteMapping("/me")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "removeProfile", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> deleteProfile(Authentication principal) {
        return usersService.deleteUser(principal.getName());
    }

    @PutMapping("/{userKey}/follow")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "followUser", security = @SecurityRequirement(name = "firebase"))
    public Mono<ExtendedUserDto> followUser(
            @PathVariable String userKey,
            Authentication principal
    ) {
        return followsService.followUser(userKey, principal.getName())
                .flatMap(_u -> usersService.getExtendedUser(userKey, maybeAuthorized(principal)))
                .map(userMapper::extendedUserToExtendedUserDto)
                .switchIfEmpty(Mono.error(UnknownUserException::new));
    }

    @DeleteMapping("/{userKey}/follow")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "unfollowUser", security = @SecurityRequirement(name = "firebase"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unfollowUser(@PathVariable String userKey, Authentication principal) {
        return followsService.unfollowUser(userKey, principal.getName());
    }

    @GetMapping(path = "/slug/{slug}/following")
    public Mono<List<ExtendedFollowDto>> followingBySlug(
            @RequestParam Optional<FollowType> type,
            @RequestParam Optional<String> name,
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {
        return usersService.getUserBySlug(slug)
                .flatMap(user -> following(type, name, Optional.of(user.getUserKey()), page, size, locationHeader, javaProxied, principal));
    }

    @GetMapping(path = {"/me/following", "/{userKey}/following"})
    public Mono<List<ExtendedFollowDto>> following(
            @RequestParam Optional<FollowType> type,
            @RequestParam Optional<String> name,
            @PathVariable Optional<String> userKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {

        val userLocation = locationHeader.flatMap(lh -> Optional.ofNullable(LocationWebFilter.parseLocationHeader(lh)));
        String resolvedKey = userKey.orElse(principal.getName());

        return getNetworkEventIdByKey(resolvedKey, "follows", javaProxied)
                .flatMap(eventId -> Mono.<List<ExtendedFollowDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(followsService.getFollowing(type, name, resolvedKey, userLocation, maybeAuthorized(principal), PageRequest.of(page, size))
                        .map(followsMapper::extendedFollowToExtendedFollowDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @GetMapping(value = "/slug/{slug}/followers")
    public Mono<List<SimplifiedUserDto>> followersBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal
    ) {
        return usersService.getUserBySlug(slug)
                .flatMap(user -> followers(Optional.of(user.getUserKey()), page, size, javaProxied, principal));
    }

    @GetMapping(value = {"/{userKey}/followers", "/me/followers"})
    public Mono<List<SimplifiedUserDto>> followers(
            @PathVariable Optional<String> userKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {

        String resolvedKey = userKey.orElse(principal.getName());
        return getNetworkEventIdByKey(resolvedKey, "follows", javaProxied)
                .flatMap(eventId -> Mono.<List<SimplifiedUserDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(followsService.getUserFollowers(
                                resolvedKey,
                                maybeAuthorized(principal),
                                false,
                                PageRequest.of(page, size))
                        .map(groupMapper::simplifiedUserToSimplifiedUserDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @PutMapping(value = "/{userKey}/block")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "blockUser", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> blockUser(@PathVariable String userKey, Authentication authentication) {
        if(userKey.equals(authentication.getName())) {
            return Mono.error(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You can not block yourself"));
        }

        return usersService.blockUser(userKey, authentication.getName());
    }

    @DeleteMapping(value = "/{userKey}/block")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "unblockUser", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> unblockUser(@PathVariable String userKey, Authentication authentication) {
        return usersService.unblockUser(userKey, authentication.getName());
    }

    @GetMapping(value = "/blockedUsers")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "blockedUsers", security = @SecurityRequirement(name = "firebase"))
    public Mono<List<ExtendedUserDto>> getBlockedUsers(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication authentication) {
        return getNetworkEventIdByCollection("users", javaProxied)
                .flatMap(eventId -> Mono.<List<ExtendedUserDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.getBlockedUsers(authentication.getName(), PageRequest.of(page, size))
                        .map(userMapper::extendedUserToExtendedUserDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @GetMapping(value = "/blockedByUsers")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "blockedByUsers", security = @SecurityRequirement(name = "firebase"))
    public Mono<List<ExtendedUserDto>> getBlockedByUsers(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication authentication) {
        return getNetworkEventIdByCollection("users", javaProxied)
                .flatMap(eventId -> Mono.<List<ExtendedUserDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.getBlockedByUsers(authentication.getName(), PageRequest.of(page, size))
                        .map(userMapper::extendedUserToExtendedUserDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @GetMapping(value = {"/me/followers/pending"})
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "followersPending", security = @SecurityRequirement(name = "firebase"))
    public Mono<List<SimplifiedUserDto>> followersPending(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {

        return getNetworkEventIdByKey(principal.getName(), "follows", javaProxied)
                .flatMap(eventId -> Mono.<List<SimplifiedUserDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(followsService.getUserFollowers(
                                principal.getName(),
                                maybeAuthorized(principal), true,
                                PageRequest.of(page, size))
                        .map(groupMapper::simplifiedUserToSimplifiedUserDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @PutMapping(value = {"/followers/{followerKey}/seen"})
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "followersSeen", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> followerSeen(
            @PathVariable String followerKey,
            Authentication principal) {

        return followsService.markFollowerSeen(followerKey, principal.getName());
    }

    @GetMapping(value = {"/{userKey}/admin", "/me/admin"})
    public Mono<List<SimplifiedGroupDto>> admin(
            @PathVariable Optional<String> userKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {

        val userLocation = locationHeader.flatMap(lh -> Optional.ofNullable(LocationWebFilter.parseLocationHeader(lh)));
        String resolvedKey = userKey.orElse(principal.getName());

        return getNetworkEventIdByKey(resolvedKey, "groups", javaProxied)
                .flatMap(eventId -> Mono.<List<SimplifiedGroupDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.getAdminGroups(
                                resolvedKey,
                                maybeAuthorized(principal),
                                userLocation,
                                PageRequest.of(page, size))
                        .map(userMapper::simplifiedGroupToSimplifiedGroupDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @GetMapping(value = "/slug/{slug}/member")
    public Mono<List<SimplifiedGroupDto>> memberBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal
    ) {
        return usersService.getUserBySlug(slug)
                .flatMap(user -> member(Optional.of(user.getUserKey()), page, size, locationHeader, javaProxied, principal));
    }

    @GetMapping(value = {"/{userKey}/member", "/me/member"})
    public Mono<List<SimplifiedGroupDto>> member(
            @PathVariable Optional<String> userKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {

        val userLocation = locationHeader.flatMap(lh -> Optional.ofNullable(LocationWebFilter.parseLocationHeader(lh)));
        String resolvedKey = userKey.orElse(principal.getName());

        return getNetworkEventIdByKey(resolvedKey, "groups", javaProxied)
                .flatMap(eventId -> Mono.<List<SimplifiedGroupDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.getMemberGroups(
                                resolvedKey,
                                maybeAuthorized(principal),
                                userLocation,
                                PageRequest.of(page, size))
                        .map(userMapper::simplifiedGroupToSimplifiedGroupDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @GetMapping(value = "/slug/{slug}/groupFollower")
    public Mono<List<SimplifiedGroupDto>> groupFollowerBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal
    ) {
        return usersService.getUserBySlug(slug)
                .flatMap(user -> groupFollower(Optional.of(user.getUserKey()), page, size, locationHeader, javaProxied, principal));
    }

    @GetMapping(value = {"/{userKey}/groupFollower", "/me/groupFollower"})
    public Mono<List<SimplifiedGroupDto>> groupFollower(
            @PathVariable Optional<String> userKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestHeader(name = LocationWebFilter.COORDS_HEADER, required = false) Optional<String> locationHeader,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication principal) {

        val userLocation = locationHeader.flatMap(lh -> Optional.ofNullable(LocationWebFilter.parseLocationHeader(lh)));
        String resolvedKey = userKey.orElse(principal.getName());

        return getNetworkEventIdByKey(resolvedKey, "groups", javaProxied)
                .flatMap(eventId -> Mono.<List<SimplifiedGroupDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.getFollowerGroups(
                                resolvedKey,
                                maybeAuthorized(principal),
                                userLocation,
                                PageRequest.of(page, size))
                        .map(userMapper::simplifiedGroupToSimplifiedGroupDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    @PutMapping("/me/fcmToken")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "addFcmToken", security = @SecurityRequirement(name = "firebase"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> addFcmToken(@RequestBody String fcmToken, Authentication auth) {
        return usersService.addFcmToken(auth.getName(), fcmToken);
    }

    @DeleteMapping("/me/fcmToken")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "deleteFcmToken", security = @SecurityRequirement(name = "firebase"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteFcmToken(@RequestBody String fcmToken, Authentication auth) {
        return usersService.deleteFcmToken(auth.getName(), fcmToken);
    }

    @PutMapping("/me/setLanguage")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "setLanguage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> setLanguage(@RequestBody String language, Authentication auth) {
        return usersService.setLanguage(auth.getName(), language);
    }

    @PostMapping("/chatusers")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "lookupChatUsers", security = @SecurityRequirement(name = "firebase"))
    public Mono<List<ChatUserDto>> lookupChatUsers(
            @RequestBody List<String> userKeys,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication authentication) {
        return getNetworkEventIdByCollection("users", javaProxied)
                .flatMap(eventId -> Mono.<List<ChatUserDto>>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(usersService.lookupChatUsers(userKeys, authentication.getName())
                        .map(userMapper::chatUserToChatUserDto)
                        .buffer()
                        .singleOrEmpty()
                        .switchIfEmpty(Mono.error(new ForwardToNodeJsException())));
    }

    private Mono<String> getNetworkEventIdByKey(String key, String collectionName, boolean javaProxied) {
        if (javaProxied) {
            log.info("[network_events] Skipping key lookup: collection={}, reason=javaProxied", collectionName);
            return Mono.empty();
        }
        if (key == null) {
            log.info("[network_events] Skipping key lookup: collection={}, reason=null-key", collectionName);
            return Mono.empty();
        }
        String keyHash = hashKeyFull(key);
        log.info("[network_events] Checking key lookup: collection={}, status=pending, key={}, keyHash={}",
                collectionName, key, keyHash);
        Query query = Query.query(Criteria.where("collectionName").is(collectionName).and("status")
                        .is("pending").and("keyHash").is(keyHash))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        return mongoTemplate.find(query, org.bson.Document.class, "network_events")
                .next()
                .doOnNext(doc -> log.info(
                        "[network_events] Found key lookup match: collection={}, keyHash={}, eventId={}, provider={}, status={}, timestamp={}, cacheStatus={}, cacheError={}",
                        collectionName,
                        keyHash,
                        doc.getObjectId("_id") != null ? doc.getObjectId("_id").toHexString() : null,
                        doc.getString("provider"),
                        doc.getString("status"),
                        doc.get("timestamp"),
                        doc.getString("cacheStatus"),
                        doc.getString("cacheError")))
                .map(doc -> doc.getObjectId("_id").toHexString())
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[network_events] No key lookup match: collection={}, status=pending, keyHash={}",
                            collectionName, keyHash);
                    return Mono.empty();
                }));
    }

    private String hashKeyFull(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Error hashing key", e);
            return "";
        }
    }

    private Mono<String> getNetworkEventIdByCollection(String collectionName, boolean javaProxied) {
        if (javaProxied) {
            log.info("[network_events] Skipping collection lookup: collection={}, reason=javaProxied", collectionName);
            return Mono.empty();
        }
        log.info("[network_events] Checking collection lookup: collection={}, status=pending", collectionName);
        Query query = Query.query(Criteria.where("collectionName").is(collectionName).and("status")
                        .is("pending"))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        return mongoTemplate.find(query, org.bson.Document.class, "network_events")
                .next()
                .doOnNext(doc -> log.info(
                        "[network_events] Found collection lookup match: collection={}, eventId={}, provider={}, status={}, timestamp={}, cacheStatus={}, cacheError={}",
                        collectionName,
                        doc.getObjectId("_id") != null ? doc.getObjectId("_id").toHexString() : null,
                        doc.getString("provider"),
                        doc.getString("status"),
                        doc.get("timestamp"),
                        doc.getString("cacheStatus"),
                        doc.getString("cacheError")))
                .map(doc -> doc.getObjectId("_id").toHexString())
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[network_events] No collection lookup match: collection={}, status=pending", collectionName);
                    return Mono.empty();
                }));
    }
}
