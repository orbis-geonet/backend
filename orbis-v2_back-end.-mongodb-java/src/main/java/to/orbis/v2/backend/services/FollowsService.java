package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.models.FollowType;
import to.orbis.v2.backend.models.Language;
import to.orbis.v2.backend.models.entity.ExtendedFollow;
import to.orbis.v2.backend.models.entity.Follow;
import to.orbis.v2.backend.models.entity.SimplifiedUser;
import to.orbis.v2.backend.models.entity.User;
import to.orbis.v2.backend.repositories.*;

import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class FollowsService {

    UsersRepository usersRepository;
    GroupsRepository groupsRepository;
    PlacesRepository placesRepository;
    FollowsRepository followsRepository;
    FollowsAggregationRepository followsAggregationRepository;
    NotificationsService notificationsService;
    UsersService usersService;

    public Mono<Follow> followUser(String userKeyToFollow, String followerUserKey) {
        return usersRepository.findOneByUserKey(userKeyToFollow)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("User key you trying to subscribe to is not found")))
                .flatMap(userToFollow -> usersRepository.findOneByUserKey(followerUserKey)
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Your user is not found")))
                        .filter(fu -> !userToFollow.isAccountPrivate() || !fu.getBlockedBy().contains(userKeyToFollow))
                        .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Private profile has blocked you")))
                        .flatMap(followerUser -> followsRepository.findFirstByFollowerKeyAndUserKey(followerUser.getUserKey(), userToFollow.getUserKey())
                                .switchIfEmpty(followsRepository.save(Follow.newUserFollow(followerUser, userToFollow))
                                        .map(newFollow -> {

                                            if (newFollow.isAccepted()) {
                                                notificationsService.notifyNewFollower(userToFollow, followerUser)
                                                        .subscribeOn(Schedulers.boundedElastic())
                                                        .publishOn(Schedulers.boundedElastic())
                                                        .subscribe(_ignored -> {
                                                                },
                                                                error -> log.error("Failed to notify {} about new follower {}",
                                                                        userKeyToFollow, followerUserKey));
                                            } else {
                                                notificationsService.notifyNewFollowRequest(userToFollow, followerUser)
                                                        .subscribeOn(Schedulers.boundedElastic())
                                                        .publishOn(Schedulers.boundedElastic())
                                                        .subscribe(_ignored -> {
                                                                },
                                                                error -> log.error("Failed to notify {} about new follow request {}",
                                                                        userKeyToFollow, followerUserKey));
                                            }
                                            return newFollow;
                                        }))));
    }

    public Mono<Void> followPlace(String placeKeyToFollow, String followerUserKey) {
        return placesRepository.findOneByPlaceKey(placeKeyToFollow)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Place key you trying to subscribe to is not found")))
                .flatMap(placeToFollow -> usersRepository.findOneByUserKey(followerUserKey)
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Your user is not found")))
                        .flatMap(followerUser -> followsRepository.findFirstByFollowerKeyAndPlaceKey(followerUser.getUserKey(), placeToFollow.getPlaceKey())
                                .switchIfEmpty(followsRepository.save(Follow.newPlaceFollow(followerUser, placeToFollow))).then()));
    }

    public Mono<Void> followGroup(String groupKeyToFollow, String followerUserKey) {
        return groupsRepository.findOneByGroupKeyAndDeletedFalse(groupKeyToFollow)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Group key you trying to subscribe to is not found")))
                .flatMap(groupToFollow -> usersRepository.findOneByUserKey(followerUserKey)
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Your user is not found")))
                        .flatMap(followerUser -> followsRepository.findFirstByFollowerKeyAndGroupKey(followerUser.getUserKey(), groupToFollow.getGroupKey())
                                .switchIfEmpty(followsRepository.save(Follow.newGroupFollow(followerUser, groupToFollow))).then()));
    }

    public Mono<Void> unfollowPlace(String placeKey, String followerKey) {
        return followsRepository.deleteByFollowerKeyAndPlaceKey(followerKey, placeKey);
    }

    public Mono<Void> unfollowGroup(String groupKey, String followerKey) {
        return followsRepository.deleteByFollowerKeyAndGroupKey(followerKey, groupKey);
    }

    public Mono<Void> unfollowUser(String userKey, String followerKey) {
        return followsRepository.deleteByFollowerKeyAndUserKey(followerKey, userKey);
    }

    public Flux<ExtendedFollow> getFollowing(Optional<FollowType> type, Optional<String> name, String followerUserKey,
                                             Optional<GeoJsonPoint> userLocation, Optional<String> userKey, Pageable pageable) {
        val ul = Mono.justOrEmpty(userLocation)
                .map(Optional::of)
                .switchIfEmpty(Mono.justOrEmpty(userKey)
                        .flatMap(usersRepository::findOneByUserKey)
                        .map(u -> Optional.ofNullable(u.getCoordinates())))
                .switchIfEmpty(Mono.just(Optional.empty()));

        return usersService.checkFollower(followerUserKey, userKey)
                .flatMapMany(_ignored ->
                        ul.flatMapMany(point ->
                                followsAggregationRepository.getFollowing(type, name, followerUserKey, point, pageable)));
    }

    public Flux<SimplifiedUser> getUserFollowers(String userKey, Optional<String> viewerKey, boolean pending, Pageable pageable) {
        return usersService
                .checkFollower(userKey, viewerKey)
                .flatMapMany(_ignored -> followsAggregationRepository.getUserFollowers(userKey, pending, pageable));
    }

    public Flux<SimplifiedUser> getGroupFollowers(String groupKey, Pageable pageable) {
        return followsAggregationRepository.getGroupFollowers(groupKey, pageable);
    }

    public Flux<SimplifiedUser> getPlaceFollowers(String placeKey, Pageable pageable) {
        return followsAggregationRepository.getPlaceFollowers(placeKey, pageable);
    }

    public Mono<Follow> acceptFollower(String followerKey, String userKey) {
        return followsRepository.findFirstByFollowerKeyAndUserKey(followerKey, userKey)
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending follow request")))
                .flatMap(follow -> followsRepository.save(follow.setAccepted(true)));
    }

    public Mono<Void> declineFollower(String followerKey, String userKey) {
        return followsRepository.deleteAllByFollowerKeyAndUserKeyAndAcceptedFalse(followerKey, userKey);
    }

    public Mono<Void> markFollowerSeen(String followerKey, String userKey) {
        return followsRepository.findFirstByFollowerKeyAndUserKey(followerKey, userKey)
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending follow request")))
                .flatMap(follow -> followsRepository.save(follow.setSeen(true)))
                .then();
    }
}
