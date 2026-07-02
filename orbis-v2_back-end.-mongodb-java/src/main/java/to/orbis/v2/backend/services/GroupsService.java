package to.orbis.v2.backend.services;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.bson.types.ObjectId;
import org.hibernate.validator.constraints.Range;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import to.orbis.v2.backend.exceptions.GroupExistsException;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.mappers.GroupMapper;
import to.orbis.v2.backend.models.StripeAccountStatus;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.repositories.*;
import to.orbis.v2.backend.repositories.queries.GroupQuery;
import to.orbis.v2.backend.utils.OrbisBeanUtils;
import to.orbis.v2.backend.utils.SlugUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;

@Service
@RequiredArgsConstructor
public class GroupsService {

    GroupsRepository groupsRepository;
    UsersRepository usersRepository;
    GroupsAggregationsRepository groupsAggregationsRepository;
    GroupMapper groupMapper;
    ShortLinksService shortLinksService;
    NotificationsService notificationsService;
    PlacesAggregationsRepository placesAggregationsRepository;
    CheckinRepository checkinRepository;
    UsersService usersService;
    StripeAccountRepository stripeAccountRepository;
    FollowsService followsService;
    PostsAggregationsRepository postsAggregationsRepository;

    public Flux<ExtendedGroup> findGroups(GroupQuery groupQuery) {
        // if there's no user location, but user in known - use user's last know location if any
        val userLocation = groupQuery.getUserLocation().map(data -> Mono.just(Optional.of(data)))
                .or(() -> groupQuery.getUserKey().map(uk -> usersRepository.findOneByUserKey(uk).map(user -> Optional.ofNullable(user.getCoordinates()))))
                .orElse(Mono.empty())
                .switchIfEmpty(Mono.just(Optional.empty()));

        return userLocation.flatMapMany(ul -> groupsAggregationsRepository.findAll(groupQuery, ul)
                .map(group -> {
                    if (groupQuery.getUserKey().isPresent()
                            && Objects.nonNull(group.getMainUserStripeAccount()) && !groupQuery.getUserKey().get().equals(group.getMainUserStripeAccount().getUserKey())) {
                        group.setMainUserStripeAccount(null);
                    }
                    return group;
                }));
    }

    public Flux<String> findGroupsForMap(
            Double latitude,
            Double longitude,
            Double distance,
            Pageable pageable
    ) {
        GeoJsonPoint point = new GeoJsonPoint(longitude, latitude);
        Distance distanceInKilometers = new Distance(distance, Metrics.KILOMETERS);

        return placesAggregationsRepository.findGroupsForMap(point, distanceInKilometers, pageable);
    }

    public Mono<ExtendedGroup> createGroup(Group group, String adminUserKey) {
        if (isNullOrEmpty(group.getGroupKey())) {
            group.setId(new ObjectId());
            group.setGroupKey(group.getId().toHexString());
        }

        if (group.getTimestamp() == null) {
            group.setTimestamp(Instant.now());
        }

        return groupsRepository.findByNameAndDeletedFalse(group.getName())
                .flatMap(existingGroup -> Mono.<ExtendedGroup>error(GroupExistsException::new))
                .switchIfEmpty(innerCreateGroup(group, adminUserKey));
    }

    private Mono<ExtendedGroup> innerCreateGroup(Group group, String adminUserKey) {
        return usersService.findUser(adminUserKey, "User creating group is not found")
                .flatMap(user -> {
                    group.setMainAdmin(user.getUserKey());
                    group.getAdmins().add(user.getUserKey());
                    group.getMembers().add(user.getUserKey());
                    group.getFollowers().add(user.getUserKey());

                    return usersRepository.save(user)
                            .flatMap(u -> {
                                var emptySlug = SlugUtils.createEmptySlug(group.getName());
                                return groupsRepository.countByEmptySlug(emptySlug)
                                        .flatMap(count -> {
                                            var slug = SlugUtils.getSlugNames(emptySlug, group.getGroupKey(), count);
                                            group.setSlug(slug);
                                            group.setEmptySlug(emptySlug);
                                            return shortLinksService.generateShortGroupLink(slug, group.getName(), group.getDescription());
                                        })
                                        .flatMap(sl -> {
                                            group.setShareLink(sl.getShortLink());
                                            group.setFullShareLink(sl.getFullLink());
                                            return groupsRepository.save(group);
                                        });
                            })
                            .flatMap(g -> followsService.followGroup(group.getGroupKey(), adminUserKey)
                                    .then(Mono.just(g)))
                            .map(groupMapper::groupToExtendedGroup);
                });
    }

    public Mono<String> shareGroup(String groupKey) {
        return groupsRepository.findOneByGroupKeyAndDeletedFalse(groupKey)
                .flatMap(group -> {
                    if ((group.getShareLink() == null || group.getShareLink().isBlank())) {
                        var emptySlug = SlugUtils.createEmptySlug(group.getName());
                        return groupsRepository.countByEmptySlug(emptySlug)
                                .flatMap(count -> {
                                    var slug = SlugUtils.getSlugNames(emptySlug, group.getGroupKey(), count);
                                    group.setSlug(slug);
                                    group.setEmptySlug(emptySlug);
                                    return shortLinksService.generateShortGroupLink(slug, group.getName(), group.getDescription());
                                })
                                .flatMap(sl -> {
                                    group.setShareLink(sl.getShortLink());
                                    group.setFullShareLink(sl.getFullLink());
                                    return groupsRepository.save(group)
                                            .flatMap(it -> Mono.just(it.getShareLink()));
                                });
                    } else {
                        return Mono.just(group.getShareLink());
                    }
                });
    }

    public Mono<ExtendedGroup> updateGroup(Group updatedGroup, String userKey) {
        return findGroup(updatedGroup.getGroupKey())
                .flatMap(existingGroup -> usersService.findUser(userKey, "User editing group is not found")
                        .flatMap(user -> existingGroup.getAdmins().contains(user.getUserKey())
                                ? Mono.just(existingGroup)
                                : Mono.error(() -> new AccessDeniedException("You must be admin to edit group")))
                        .map(eg -> updateFields(eg, updatedGroup))
                        .flatMap(ug -> {
                            var emptySlug = SlugUtils.createEmptySlug(ug.getName());
                            return groupsRepository.countByEmptySlug(emptySlug)
                                    .flatMap(count -> {
                                        var slug = SlugUtils.getSlugNames(emptySlug, ug.getGroupKey(), count);
                                        ug.setSlug(slug);
                                        ug.setEmptySlug(emptySlug);
                                        return shortLinksService.generateShortGroupLink(slug, ug.getName(), ug.getDescription());
                                    })
                                    .flatMap(sl -> {
                                        ug.setShareLink(sl.getShortLink());
                                        ug.setFullShareLink(sl.getFullLink());
                                        return groupsRepository.save(ug);
                                    });
                        }))
                .flatMap(g -> groupsAggregationsRepository
                        .findAll(GroupQuery.group(updatedGroup.getGroupKey()).forUser(userKey).listMembers(true).build(), Optional.empty())
                        .singleOrEmpty());
    }

    public Mono<Group> findGroupAndCheckMainAdmin(String groupKey, String userKey, String errorMessage) {
        return findGroup(groupKey)
                .flatMap(group -> usersService.findUser(userKey, "User editing group is not found")
                        .flatMap(user -> {
                            if (Objects.isNull(group.getMainAdmin())) {
                                group.setMainAdmin(userKey);
                                return groupsRepository.save(group)
                                        .flatMap(Mono::just);
                            } else if (user.getUserKey().equals(group.getMainAdmin())) {
                                if (!Boolean.TRUE.equals(group.getIsSubscriptionActivate())) {
                                    return stripeAccountRepository.findByUserKeyAndDeletedFalse(user.getUserKey())
                                            .flatMap(stripeAccount -> {
                                                if (stripeAccount.getStatus().equals(StripeAccountStatus.READY_TO_USE)) {
                                                    group.setIsSubscriptionActivate(true);
                                                    group.setTimestamp(Instant.now());
                                                    return groupsRepository.save(group)
                                                            .flatMap(Mono::just);
                                                } else {
                                                    return Mono.just(group);
                                                }
                                            })
                                            .switchIfEmpty(Mono.just(group));
                                } else {
                                    return Mono.just(group);
                                }
                            } else {
                                return Mono.error(() -> new AccessDeniedException(errorMessage));
                            }
                        })
                );
    }

    private Group updateFields(Group existingGroup, Group incomingGroup) {

        OrbisBeanUtils.copyNotNullPropertiesSkipping(Group.class, existingGroup, incomingGroup, Group.Fields.groupKey,
                Group.Fields.admins, Group.Fields.deleted, Group.Fields.followers, Group.Fields.mainAdmin,
                Group.Fields.members, Group.Fields.isSubscriptionActivate);

        return existingGroup;
    }

    private Mono<Void> addUserToGroup(String groupKey, String userKey, BiFunction<Group, User, Mono<Tuple2<Group, User>>> addUserFunc) {

        return findGroup(groupKey)
                .flatMap(group -> usersService.findUser(userKey, "User not found")
                        .flatMap(user -> addUserFunc.apply(group, user)))
                .flatMap(t -> groupsRepository.save(t.getT1())
                        .flatMap(g -> usersRepository.save(t.getT2())))
                .then();
    }

    public Mono<Group> findGroup(String groupKey) {
        return groupsRepository.findOneByGroupKeyAndDeletedFalse(groupKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Group not found")));
    }

    public Mono<Long> countGroupWithActivatedSubscriptionsByMainAdmin(String mainAdminKey) {
        return groupsRepository.countByMainAdminAndDeletedFalseAndIsSubscriptionActivateTrue(mainAdminKey)
                .defaultIfEmpty(0L);
    }

    private Mono<Void> modifyMembership(String groupKey, String userKey, String removedBy,
                                        BiFunction<Group, User, Mono<Tuple2<Group, User>>> modifyMembershipFunc) {
        return modifyMembership(groupKey, userKey, removedBy, true, modifyMembershipFunc, "remove");
    }

    private Mono<Void> modifyMembership(String groupKey, String userKey, String removedBy, boolean allowSelfService,
                                        BiFunction<Group, User, Mono<Tuple2<Group, User>>> modifyMembershipFunc, String action) {
        return findGroup(groupKey)
                .flatMap(group -> usersService.findUser(userKey, "User not found")
                        .flatMap(userBeingRemoved -> usersService.findUser(removedBy, "User removing member not found")
                                .flatMap(removedByUser -> {
                                    if (allowSelfService && removedByUser.getUserKey().equals(userBeingRemoved.getUserKey())
                                            || group.getAdmins().contains(removedByUser.getUserKey())
                                            || removedByUser.isSuperAdmin()) {
                                        return modifyMembershipFunc.apply(group, userBeingRemoved);
                                    } else {
                                        return Mono.error(() -> new AccessDeniedException("You must be admin to " + action + " a user"));
                                    }
                                })))
                .flatMap(t -> groupsRepository.save(t.getT1()).flatMap(g -> usersRepository.save(t.getT2())))
                .then();
    }

    public Mono<Void> addMember(String groupKey, String userKey) {
        return addUserToGroup(groupKey, userKey, (g, u) -> {
            g.getMembers().add(u.getUserKey());
            g.getFollowers().add(u.getUserKey());

            return followsService.followGroup(groupKey, userKey)
                    .then(Mono.just(Tuples.of(g, u)));
        });
    }

    public Mono<Void> removeMember(String groupKey, String userKey, String removedBy) {
        return modifyMembership(groupKey, userKey, removedBy, (g, u) -> {

            if (g.getAdmins().contains(u.getUserKey()) && g.getAdmins().size() == 1) {
                return Mono.error(() -> new AccessDeniedException("You are the last admin and can not remove yourself. Promote members or remove group."));
            }

            g.getMembers().remove(u.getUserKey());
            g.getFollowers().remove(u.getUserKey());
            g.getAdmins().remove(u.getUserKey());

            return followsService.unfollowGroup(groupKey, userKey)
                    .then(Mono.just(Tuples.of(g, u)));
        });
    }

    public Mono<Void> hideStories(String groupKey, String userKey) {
        return addUserToGroup(groupKey, userKey, (g, u) -> {
            g.getStoriesHidden().add(u.getUserKey());

            return Mono.just(Tuples.of(g, u));
        });
    }

    public Mono<Void> unhideStories(String groupKey, String userKey) {
        return modifyMembership(groupKey, userKey, userKey, (g, u) -> {

            g.getStoriesHidden().remove(u.getUserKey());

            return Mono.just(Tuples.of(g, u));
        });
    }

    public Mono<Void> addFollower(String groupKey, String userKey) {
        return addUserToGroup(groupKey, userKey, (g, u) -> {
            g.getFollowers().add(u.getUserKey());
            return followsService.followGroup(groupKey, userKey)
                    .then(Mono.just(Tuples.of(g, u)));
        });
    }

    public Mono<Void> removeFollower(String groupKey, String userKey, String removedBy) {
        return modifyMembership(groupKey, userKey, removedBy, (g, u) -> {
            g.getFollowers().remove(u.getUserKey());
            return followsService.unfollowGroup(groupKey, userKey)
                    .then(Mono.just(Tuples.of(g, u)));
        });
    }

    public Mono<Void> addAdmin(String groupKey, String userKey, String addedBy) {
        return modifyMembership(groupKey, userKey, addedBy, false, (g, u) -> {
            if (!g.getMembers().contains(u.getUserKey())) {
                return Mono.error(() -> new AccessDeniedException("User must be a member to be promoted to admins"));
            }
            g.getAdmins().add(u.getUserKey());
            return Mono.just(Tuples.of(g, u));
        }, "add");
    }

    public Mono<Void> removeAdmin(String groupKey, String userKey, String removedBy) {
        return modifyMembership(groupKey, userKey, removedBy, (g, u) -> {
            if (g.getAdmins().contains(u.getUserKey()) && g.getAdmins().size() == 1) {
                return Mono.error(() -> new AccessDeniedException("You are the last admin and can not remove yourself. Promote members or remove group."));
            }
            g.getAdmins().remove(u.getUserKey());
            return Mono.just(Tuples.of(g, u));
        });
    }

    public Mono<Void> addBan(String groupKey, String userKey, String addedBy) {
        return modifyMembership(groupKey, userKey, addedBy, false, (g, u) -> {

            g.getBanned().add(u.getUserKey());
            return Mono.just(Tuples.of(g, u));
        }, "ban");
    }

    public Mono<Void> removeBan(String groupKey, String userKey, String removedBy) {
        return modifyMembership(groupKey, userKey, removedBy, false, (g, u) -> {

            g.getBanned().remove(u.getUserKey());
            return Mono.just(Tuples.of(g, u));
        }, "unban");
    }

    public Mono<Void> deleteGroup(String groupKey, String userKey) {
        return usersService.findUser(userKey, "User not found")
                .flatMap(user -> findGroup(groupKey)
                        .flatMap(group -> {
                            if (!group.getAdmins().contains(user.getUserKey()) && !user.isSuperAdmin()) {
                                return Mono.error(() -> new AccessDeniedException("You must be admin to delete group"));
                            }

                            return groupsRepository.save(group.setDeleted(true));
                        })
                        .flatMap(this::deletePosts))
                .then();
    }

    public Flux<List<SimplifiedGroup>> findRecommendedGroups(GeoJsonPoint point, int size, String userKey) {
        return groupsAggregationsRepository.findLastInteractions(userKey, size, point)
                .buffer()
                .switchIfEmpty(Flux.just(new ArrayList<>()))
                .flatMap(lastGroups -> {
                    if (lastGroups.size() >= size) {
                        return Flux.just(lastGroups);
                    }

                    return groupsAggregationsRepository.findLastActiveAround(point, size - lastGroups.size(), lastGroups)
                            .buffer()
                            .switchIfEmpty(Flux.just(new ArrayList<>()))
                            .map(activeGroup -> Stream.concat(lastGroups.stream(), activeGroup.stream()).collect(Collectors.toList()));
                }).flatMap(lg -> {
                    if (lg.size() >= size) {
                        return Flux.just(lg);
                    }

                    return groupsAggregationsRepository.sampleGroups(point, size - lg.size(), lg)
                            .buffer()
                            .switchIfEmpty(Flux.just(new ArrayList<>()))
                            .map(sg -> Stream.concat(lg.stream(), sg.stream()).collect(Collectors.toList()));
                });
    }

    public Mono<GroupStripeAccountInfo> getStripeAccountGroupInfo(String groupKey) {
        return groupsAggregationsRepository.findMainAdminStripeInfo(groupKey);
    }

    public Flux<List<SimplifiedGroup>> findRatedGroups(GeoJsonPoint point, int timePeriod, int size) {
        val presentPeriodStart = Instant.now().minus(timePeriod, ChronoUnit.DAYS);
        val presentPeriodEnd = Instant.now();
        val pastPeriodStart = Instant.now().minus(2L * timePeriod, ChronoUnit.DAYS);

        return Flux.zip(
                groupsAggregationsRepository.findRatedGroupsForPeriod(point, pastPeriodStart, presentPeriodStart, size)
                        .buffer()
                        .switchIfEmpty(Flux.just(new ArrayList<>())),

                groupsAggregationsRepository.findRatedGroupsForPeriod(point, presentPeriodStart, presentPeriodEnd, size)
                        .buffer()
                        .switchIfEmpty(Flux.just(new ArrayList<>())),
                (past, present) -> buildRating(past, present, size)
        ).flatMap(foundRated -> {
            int delta = size - foundRated.size();
            if (delta <= 0) {
                return Flux.just(foundRated);
            }

            return mergeNewlyFoundGroups(foundRated,
                    groupsAggregationsRepository.findLastActiveAround(point, delta, foundRated));

        }).flatMap(foundSoFar -> {
            int delta = size - foundSoFar.size();
            if (delta <= 0) {
                return Flux.just(foundSoFar);
            }

            return mergeNewlyFoundGroups(foundSoFar,
                    findGroups(GroupQuery.closeTo(point)
                            .withPageable(PageRequest.of(0, delta))
                            .setSkipGroups(foundSoFar.stream().map(SimplifiedGroup::getGroupKey).collect(Collectors.toList()))
                            .build()).cast(SimplifiedGroup.class));

        });
    }

    private Flux<List<SimplifiedGroup>> mergeNewlyFoundGroups(List<SimplifiedGroup> foundRated, Flux<SimplifiedGroup> mergeRequest) {
        return mergeRequest
                .buffer()
                .switchIfEmpty(Flux.just(new ArrayList<>()))
                .flatMap(foundNear -> {
                    int lastRank = foundRated.size();
                    return Flux.mergeSequential(
                                    Flux.fromIterable(foundRated),
                                    Flux.fromIterable(foundNear).index()
                                            .map(t -> t.getT2().setRankDiff(0).setRank((int) (t.getT1() + lastRank))))
                            .buffer()
                            .switchIfEmpty(Flux.just(Collections.emptyList()));
                });
    }

    private List<SimplifiedGroup> buildRating(List<SimplifiedGroup> past, List<SimplifiedGroup> present, int size) {

        // if no new rank found - use old rank
        if (present.size() == 0) return past.stream().peek(c -> c.setRankDiff(0)).collect(Collectors.toList());

        int defaultPosition = present.size();
        Map<String, Integer> presentMap = present.stream().collect(Collectors.toMap(SimplifiedGroup::getGroupKey, SimplifiedGroup::getRank));
        Map<String, Integer> pastMap = past.stream().collect(Collectors.toMap(SimplifiedGroup::getGroupKey, SimplifiedGroup::getRank));

        List<SimplifiedGroup> res = new ArrayList<>(size);

        for (final SimplifiedGroup cur : present) {
            val prevRank = pastMap.getOrDefault(cur.getGroupKey(), defaultPosition);
            cur.setRankDiff(prevRank - cur.getRank());
            res.add(cur);
        }

        int i = 0;
        int lastRank = present.get(present.size() - 1).getRank();
        while (res.size() < size && i < pastMap.size()) {
            val curPast = past.get(i);
            i++;
            if (presentMap.containsKey(curPast.getGroupKey())) {
                continue;
            }
            int prevRank = curPast.getRank();
            res.add(curPast.setRank(++lastRank).setRankDiff(prevRank - lastRank));
        }

        return res;
    }

    public Flux<SimplifiedUser> getGroupMembers(String groupKey, Pageable pageable) {
        return groupsAggregationsRepository.findGroupUsers(groupKey, Group.Fields.members, pageable);
    }

    public Flux<SimplifiedUser> getGroupAdmins(String groupKey, Pageable pageable) {
        return groupsAggregationsRepository.findGroupUsers(groupKey, Group.Fields.admins, pageable);
    }

    public Mono<Void> deletePosts(Group group) {
        return postsAggregationsRepository.deletePosts(group.getGroupKey())
                .then(checkinRepository.deleteAllByGroupKey(group.getGroupKey()))
                .then(placesAggregationsRepository.unsetDominantGroup(group.getGroupKey()))
                .then();
    }

    public Flux<SimplifiedUser> getGroupBanned(String groupKey, Pageable pageable) {
        return groupsAggregationsRepository.findGroupUsers(groupKey, Group.Fields.banned, pageable);
    }

    public Mono<Void> saveGroup(Group group) {
        return groupsRepository.save(group)
                .then();
    }

    public Mono<String> reportGroup(String groupKey, String reason, Optional<String> userKey) {
        return groupsAggregationsRepository.findAll(GroupQuery.group(groupKey).build(), Optional.empty())
                .singleOrEmpty()
                .flatMap(g -> userKey.map(usersRepository::findOneByUserKey).orElse(Mono.empty()).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
                        .flatMap(u -> {
                            if (g.isReported()) {
                                return Mono.just("Group is already reported");
                            }

                            return groupsAggregationsRepository.setReported(g.getGroupKey(), reason)
                                    .flatMap(_ignored -> sendReport(g, reason, u));
                        }));

    }

    public Mono<Void> setSubscriptionActivated(String mainUserKey) {
        return groupsAggregationsRepository.setSubscriptionActivated(mainUserKey)
                .then();
    }

    private Mono<String> sendReport(ExtendedGroup group, String reason, Optional<User> user) {
        var originalGroup = groupMapper.extendedGroupToGroup(group);
        var emptySlug = SlugUtils.createEmptySlug(originalGroup.getName());

        Mono<String> shortLink = (group.getShareLink() == null || group.getShareLink().isEmpty())
                ? groupsRepository.countByEmptySlug(emptySlug)
                        .flatMap(count -> {
                            var slug = SlugUtils.getSlugNames(emptySlug, originalGroup.getGroupKey(), count);
                            originalGroup.setSlug(slug);
                            originalGroup.setEmptySlug(emptySlug);
                            return shortLinksService.generateShortGroupLink(slug, originalGroup.getName(), originalGroup.getDescription());
                        })
                        .flatMap(sl -> {
                            originalGroup.setShareLink(sl.getShortLink());
                            originalGroup.setFullShareLink(sl.getFullLink());
                            return groupsRepository.save(originalGroup);
                        })
                        .flatMap(sl -> Mono.just(sl.getShareLink()))
                : Mono.just(group.getShareLink());

        return shortLink.flatMap(sl -> {
            val title = String.format("Group '%s' was reported", group.getName());

            var body = "Reason: " + reason + "\n" +
                    "Group details:\n";

            body += "Open in app: " + sl + "\n";
            body += "Description: " + group.getDescription() + "\n";
            body += "\n";

            if (user.isPresent()) {
                body += "User reported: \n";
                body += "    " + user.get().getDisplayName() + "\n";
            }

            return notificationsService.reportGroup(title, body, group, user).map(_ignored -> "Group was reported");
        });
    }

    public Mono<Void> removePlaceFromGroup(String groupKey, String placeKey, Optional<String> userKey) {
        if (userKey.isEmpty()) {
            return Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Must be logged in to remove place from group"));
        }

        val uk = userKey.get();

        return groupsRepository.findOneByGroupKeyAndDeletedFalse(groupKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Group not found")))
                .flatMap(group -> placesAggregationsRepository.findOneByPlaceKeyWithCompeting(placeKey, Optional.empty())
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Place not found")))
                        .flatMap(place -> usersRepository.findOneByUserKey(uk)
                                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("User not found")))
                                .flatMap(user -> (group.getAdmins().contains(uk) || user.isSuperAdmin())
                                        ? doRemovePlaceFromGroup(group, place)
                                        : Mono.error(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Must be admin to remove place from group")))))
                .then();
    }

    private Mono<UpdateResult> doRemovePlaceFromGroup(Group group, ExtendedPlace place) {
        return checkinRepository.deleteAllByPlaceKeyAndGroupKey(place.getPlaceKey(), group.getGroupKey())
                .then(postsAggregationsRepository.deleteAllByPlaceAndGroup(place, group))
                .then(placesAggregationsRepository.replaceDominantGroup(place, group));
    }

    public Mono<Void> blockGroup(String groupKey, String userKey) {
        return groupsAggregationsRepository.blockGroup(groupKey, userKey);
    }

    public Mono<Void> unblockGroup(String groupKey, String userKey) {
        return groupsAggregationsRepository.unblockGroup(groupKey, userKey);
    }

    public Mono<Group> getGroupBySlug(String slug) {
        return groupsRepository.findAllBySlug(slug).next();
    }
}
