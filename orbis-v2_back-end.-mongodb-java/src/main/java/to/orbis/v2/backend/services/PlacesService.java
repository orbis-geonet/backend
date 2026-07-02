package to.orbis.v2.backend.services;

import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.PlaceRecentlyCreatedException;
import to.orbis.v2.backend.mappers.GroupMapper;
import to.orbis.v2.backend.mappers.PlaceMapper;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.dto.*;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.models.requests.places.RatePlaceRequest;
import to.orbis.v2.backend.repositories.*;
import to.orbis.v2.backend.utils.OrbisBeanUtils;
import to.orbis.v2.backend.utils.SlugUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlacesService {

    PlacesRepository placesRepository;
    PlacesAggregationsRepository placesAggregationsRepository;
    GroupsRepository groupRepository;
    GroupMapper groupMapper;
    PostService postService;
    GooglePlaceService googlePlaceService;
    FollowsRepository followsRepository;
    PlaceRateRepository placeRateRepository;
    ShortLinksService shortLinksService;
    UsersRepository usersRepository;
    NotificationsService notificationsService;
    PlaceMapper placeMapper;

    public Flux<ExtendedPlace> findPlaces(Optional<GeoJsonPoint> location, Double distance,
                                          Optional<String> name, Optional<String> ownedByGroupKey,
                                          Optional<String> auth, Pageable pageable) {

        return location
                .map(point -> placesAggregationsRepository
                        .findByCoordinatesNear(point, distance, name, ownedByGroupKey, false, auth, pageable)
                )
                .orElseGet(() -> placesAggregationsRepository.findAll(name, ownedByGroupKey, auth, pageable));
    }

    public Mono<Place> createPlace(Place place, GeoJsonPoint point) {
        if (Strings.isNullOrEmpty(place.getPlaceKey())) {
            place.setId(new ObjectId());
            place.setPlaceKey(place.getId().toHexString());
        }

        return groupRepository.findOneByGroupKeyAndDeletedFalse(place.getGroupCreatedKey())
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Please provide existing group to create a place")))
                .flatMap(group -> placesRepository.findFirstByUserCreatedKeyOrderByTimestampDesc(place.getUserCreatedKey())
                        .flatMap(lastPlace -> {
                            if (lastPlace.getGroupCreatedKey() != null
                                    && lastPlace.getGroupCreatedKey().equals(place.getGroupCreatedKey())
                                    && Duration.between(lastPlace.getTimestamp(), Instant.now()).toSeconds() < 1) {
                                return Mono.error(PlaceRecentlyCreatedException::new);
                            }

                            return Mono.just(group);
                        })
                        .switchIfEmpty(Mono.just(group)))
                .flatMap(group -> googlePlaceService.findPlaceAddress(place.getCoordinates())
                        .flatMap(googlePlace -> {
                            var googleAddress = googlePlace.getGoogleAddress();
                            googleAddress.setFullAddress(googlePlace.getAddress());
                            place.setGoogleAddress(googleAddress);
                            return Mono.just(group);
                        })
                        .switchIfEmpty(Mono.just(group)))
                .flatMap(group -> {
                    var emptySlug = SlugUtils.createEmptySlug(place.getName());
                    return placesRepository.countByEmptySlug(emptySlug)
                            .flatMap(count -> {
                                var slug = SlugUtils.getSlugNames(emptySlug, place.getPlaceKey(), count);
                                place.setSlug(slug);
                                place.setEmptySlug(emptySlug);
                                return shortLinksService.generateShortGroupLink(slug, place.getName(), place.getDescription());
                            })
                            .flatMap(sl -> {
                                group.setShareLink(sl.getShortLink());
                                group.setFullShareLink(sl.getFullLink());
                                return Mono.just(group);
                            })
                            .switchIfEmpty(Mono.just(group));
                })
                .flatMap(group -> placesRepository.save(place)
                        .flatMap(saved -> createPost(saved, group, place.getCoordinates())
                                .flatMap(post -> {
                                    saved.setLastSize(500.0)
                                                    .setDominantGroupKey(saved.getGroupCreatedKey())
                                                    .setLastSizeChangeTimestamp(Instant.now())
                                                    .setLastCheckInTimestamp(Instant.now())
                                                    .setCheckInPolygonCoordinateKey(post.getCheckInPolygonCoordinateKey());
                                    return placesRepository.save(saved);
                                })));
    }

    private Mono<Post> createPost(Place saved, Group group, GeoJsonPoint point) {
        val post = new Post();
        post.setCoordinates(point);
        post.setGroupKey(saved.getGroupCreatedKey());
        post.setPlaceKey(saved.getPlaceKey());
        post.setUserKey(saved.getUserCreatedKey());
        post.setType(PostType.CHECK_IN);
        post.setTimestamp(Instant.now());
        return postService.doPost(post, Optional.of(group), Optional.of(saved), true);
    }

    public Mono<Place> updatePlace(String placeKey, Place incomingPlace) {
        return placesRepository.findOneByPlaceKey(placeKey)
                .map(existingPlace -> updateFields(existingPlace, incomingPlace))
                .flatMap(place -> {
                    var emptySlug = SlugUtils.createEmptySlug(place.getName());
                    return placesRepository.countByEmptySlug(emptySlug)
                            .flatMap(count -> {
                                var slug = SlugUtils.getSlugNames(emptySlug, place.getPlaceKey(), count);
                                place.setSlug(slug);
                                place.setEmptySlug(emptySlug);
                                return shortLinksService.generateShortGroupLink(slug, place.getName(), place.getDescription());
                            })
                            .flatMap(sl -> {
                                place.setShareLink(sl.getShortLink());
                                place.setFullShareLink(sl.getFullLink());
                                return placesRepository.save(place);
                            });
                })
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Place you are trying to update does not exist")));
    }

    private Place updateFields(Place existingPlace, Place incomingPlace) {
        OrbisBeanUtils.copyNotNullPropertiesSkipping(
                Place.class,
                existingPlace,
                incomingPlace,
                Place.Fields.creationServerTimestamp);

        return existingPlace;
    }

    public Mono<ExtendedPlace> getPlace(String placeKey, Optional<String> userKey) {
        return placesAggregationsRepository.findOneByPlaceKeyWithCompeting(placeKey, userKey)
                .flatMap(ep -> (ep.getCompetingGroups() == null || ep.getCompetingGroups().isEmpty())
                        ? loadDominantGroup(ep)
                        : Mono.just(ep))
                .flatMap(ep -> userKey
                        .map(uk -> followsRepository
                                .existsByFollowerKeyAndPlaceKey(uk, ep.getPlaceKey())
                                .map(ep::setFollowing))
                        .orElse(Mono.just(ep)))
                .map(this::countScores);
    }

    private ExtendedPlace countScores(ExtendedPlace ep) {
        if (ep.getCompetingGroups() == null || ep.getCompetingGroups().isEmpty()) {
            return ep;
        }

        if (ep.getCompetingGroups().size() == 1) {
            ep.getCompetingGroups().get(0).setPercentage(100.0);
            return ep;
        }

        //noinspection OptionalGetWithoutIsPresent - always present as competing groups are never empty at this point
        val dominantGroup = ep.getCompetingGroups().stream().max(
                        Comparator.comparing(CompetingGroup::getValidCheckins)
                                .thenComparing((g1, g2) -> compareGroups(g1, g2, ep.getDominantGroupKey())))
                .get();

        dominantGroup.setPercentage(51.0);

        val sum = ep.getCompetingGroups().stream().filter(g -> !g.getGroupKey().equals(dominantGroup.getGroupKey()))
                .mapToInt(CompetingGroup::getValidCheckins)
                .sum();

        ep.getCompetingGroups().stream().filter(g -> !g.getGroupKey().equals(dominantGroup.getGroupKey()))
                .forEach(g -> g.setPercentage(49.0 * g.getValidCheckins() / sum));

        return ep;
    }

    private int compareGroups(CompetingGroup g1, CompetingGroup g2, String dominantGroupKey) {
        if (g1.getGroupKey().equals(dominantGroupKey)) return 1;
        if (g2.getGroupKey().equals(dominantGroupKey)) return -1;
        return g1.getGroupKey().compareTo(g2.getGroupKey());
    }

    private Mono<ExtendedPlace> loadDominantGroup(ExtendedPlace ep) {
        return groupRepository.findOneByGroupKeyAndDeletedFalse(ep.getDominantGroupKey())
                .map(dominantGroup -> ep.setCompetingGroups(List.of(groupMapper.groupToCompetingGroup(dominantGroup))))
                .switchIfEmpty(Mono.just(ep));
    }

    public Flux<ExtendedPlace> findPlacesForMap(
            GeoJsonPoint point, Double distance, Optional<String> ownedByGroupKey, Optional<String> auth, Pageable pageable) {
        return placesAggregationsRepository.findByCoordinatesNear(point, distance, Optional.empty(), ownedByGroupKey, true, auth, pageable);
    }

    public Flux<ExtendedPlaceDto> findPlacesDtoForMap(GeoJsonPoint point, Double distance, Optional<String> auth, Pageable pageable) {
        return findPlacesForMap(point, distance, auth, Optional.empty(), pageable)
                .map(placeMapper::extendedPlaceToExtendedPlaceDto);
    }

    public Flux<Place> fillFromGooglePlaces(Optional<GeoJsonPoint> location, Optional<String> name, int size) {
        // Google Places auto-import is disabled. Do not fetch and persist external places as a search fallback.
        return Flux.empty();
    }

    public Mono<String> sharePlace(String placeKey) {
        return placesRepository.findOneByPlaceKey(placeKey)
                .flatMap(place -> {
                    if ((place.getShareLink() == null || place.getShareLink().isBlank())) {
                        var emptySlug = SlugUtils.createEmptySlug(place.getName());
                        return placesRepository.countByEmptySlug(emptySlug)
                                .flatMap(count -> {
                                    var slug = SlugUtils.getSlugNames(emptySlug, place.getPlaceKey(), count);
                                    place.setSlug(slug);
                                    place.setEmptySlug(emptySlug);
                                    return shortLinksService.generateShortGroupLink(slug, place.getName(), place.getDescription());
                                })
                                .flatMap(sl -> {
                                    place.setShareLink(sl.getShortLink());
                                    place.setFullShareLink(sl.getFullLink());
                                    return placesRepository.save(place)
                                            .flatMap(it -> Mono.just(it.getShareLink()));
                                });
                    } else {
                        return Mono.just(place.getShareLink());
                    }
                });
    }

    public Mono<String> reportPlace(String placeKey, String reason, Optional<String> userKey) {
        return placesAggregationsRepository.findOneByPlaceKeyWithCompeting(placeKey, Optional.empty())
                .flatMap(place -> userKey.map(usersRepository::findOneByUserKey).orElse(Mono.empty()).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
                        .flatMap(u -> {
                            if (place.isReported()) {
                                return Mono.just("Place is already reported");
                            }

                            return placesAggregationsRepository.setReported(place.getPlaceKey(), reason)
                                    .flatMap(_ignored -> sendReport(place, reason, u));
                        }));

    }

    private Mono<String> sendReport(ExtendedPlace place, String reason, Optional<User> user) {
        var emptySlug = SlugUtils.createEmptySlug(place.getName());

        Mono<String> shortLink = (place.getShareLink() == null || place.getShareLink().isEmpty())
                ? placesRepository.countByEmptySlug(emptySlug)
                .flatMap(count -> {
                    var slug = SlugUtils.getSlugNames(emptySlug, place.getPlaceKey(), count);
                    place.setSlug(slug);
                    place.setEmptySlug(emptySlug);
                    return shortLinksService.generateShortGroupLink(slug, place.getName(), place.getDescription());
                })
                .flatMap(sl -> {
                    place.setShareLink(sl.getShortLink());
                    place.setFullShareLink(sl.getFullLink());
                    return placesRepository.save(place);
                })
                .flatMap(sl -> Mono.just(sl.getShareLink()))
                : Mono.just(place.getShareLink());

        return shortLink.flatMap(sl -> {
            val title = String.format("Place '%s' was reported", place.getName());

            var body = "Reason: " + reason + "\n" +
                    "Place details:\n";

            body += "Open in app: " + sl + "\n";
            body += "Description: " + place.getDescription() + "\n";
            body += "\n";

            if (user.isPresent()) {
                body += "User reported: \n";
                body += "    " + user.get().getDisplayName() + "\n";
            }

            return notificationsService.reportPlace(title, body, place, user).map(_ignored -> "Group was reported");
        });
    }

    public Mono<PlaceRateResult> ratePlace(RatePlaceRequest request, String userKey) {
        return placesRepository.findOneByPlaceKey(request.getPlaceKey())
                .flatMap(place -> placeRateRepository.findByPlaceKeyAndUserKey(place.getPlaceKey(), userKey)
                            .flatMap(rate -> {
                                var totalRate = Objects.isNull(place.getTotalRate()) ?
                                        request.getRate() : place.getTotalRate() - rate.getUserRate() + request.getRate();

                                rate.setUserRate(request.getRate());
                                place.setTotalRate(totalRate);

                                return placeRateRepository.save(rate)
                                        .flatMap(placeRate -> placesRepository.save(place));
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                var totalRate = Objects.isNull(place.getTotalRate()) ?
                                        request.getRate() : place.getTotalRate() + request.getRate();
                                place.setTotalRate(totalRate);

                                var countRate = Objects.isNull(place.getCountRates()) ?
                                        1 : place.getCountRates() + 1;
                                place.setCountRates(countRate);
                                var id = new ObjectId();
                                var rate = PlaceRate.builder()
                                        .placeRateKey(id.toHexString())
                                        .userRate(request.getRate())
                                        .placeKey(request.getPlaceKey())
                                        .userKey(userKey)
                                        .build();

                                rate.setId(id);

                                return placeRateRepository.save(rate)
                                        .flatMap(placeRate -> placesRepository.save(place));
                            }))
                )
                .flatMap(place -> Mono.just(
                        PlaceRateResult.builder()
                                .placeKey(place.getPlaceKey())
                                .averageRate(place.getTotalRate()/place.getCountRates())
                                .totalRate(place.getTotalRate())
                                .countRates(place.getCountRates())
                                .build()
                ));
    }

    public Mono<Place> getPlaceBySlug(String slug) {
        return placesRepository.findAllBySlug(slug)
                .next();
    }
}
