package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.geotools.referencing.GeodeticCalculator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.repositories.*;
import to.orbis.v2.backend.utils.SlugUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
public class CheckinService {

    UsersRepository usersRepository;
    PlacesRepository placesRepository;
    PlacesAggregationsRepository placesAggregationsRepository;
    GroupsRepository groupsRepository;
    CheckinRepository checkinRepository;
    FirebasePlacesService firebasePlacesService;
    FirebaseGroupsService firebaseGroupsService;
    ShortLinksService shortLinksService;
    GroupsAggregationsRepository groupsAggregationsRepository;

    PolygonSchedulerService polygonSchedulerService;

    public Mono<String> checkinAndReturnPolygonCoordinateKey(String groupKey, String placeKey, String userKey) {
        return usersRepository.findOneByUserKey(userKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("User not found")))
                .flatMap(user -> placesRepository.findOneByPlaceKey(placeKey)
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Place not found")))
                        .flatMap(place -> groupsRepository.findOneByGroupKeyAndDeletedFalse(groupKey)
                                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Group not found")))
                                .flatMap(group ->
                                        // don't check existing checkins and thus allow superadmin to checkin multiple times
                                        ((user.isSuperAdmin())
                                                ? Mono.<Checkin>empty()
                                                : checkinRepository.findLastValidCheckinAfterTimestamp(user.getUserKey(), place.getPlaceKey(), Instant.now().minus(1, ChronoUnit.DAYS)))
                                                // if previous checkin for this user in this place within 24h is found - refresh last checkin time if group is the same and do nothing
                                                .flatMap(checkin -> checkin.getGroupKey().equals(groupKey) ? checkinRepository.save(checkin.setValidTimestamp(Instant.now())) : Mono.just(checkin))
                                                // user checks in first time in this place for the last 24h
                                                // actually do recalculate place size and group ownership
                                                .switchIfEmpty(doCheckin(user, group, place)))))
                .map(Checkin::getPolygonSchedulerCoordinateKey);
    }

    private Mono<Checkin> doCheckin(User user, Group group, Place place) {
        return checkinRepository.save(
                Checkin.builder()
                        .validTimestamp(Instant.now())
                        .duplicated(false)
                        .eventType("CHECK_IN")
                        .groupKey(group.getGroupKey())
                        .placeKey(place.getPlaceKey())
                        .userKey(user.getUserKey())
                        .valid(true)
                        .build())
                .flatMap(checkin -> {
                    double previousSize = place.currentSize();
                    place.setTimestamp(Instant.now());
                    return placesRepository.save(place.checkin())
                            .flatMap(firebasePlacesService::save)
                            //Use it if circles should be resized after checkin
                            .flatMap(updatedPlace -> updateTouchedSizes(updatedPlace, previousSize)
//                                    .flatMap(firebasePlacesService::save)
                                    .buffer().singleOrEmpty().thenReturn(updatedPlace))
                            .flatMap(this::updateDominantGroups)
                            .thenReturn(checkin);
                })
                .flatMap(checkin -> addSlug(place)
                        .flatMap(updatedPlace -> polygonSchedulerService.addPolygonSchedulerCoordinateAfterCheckin(place))
                        .flatMap(polygonSchedulerCoordinateKey -> {
                            checkin.setPolygonSchedulerCoordinateKey(polygonSchedulerCoordinateKey);
                            return checkinRepository.save(checkin);
                        })
                );

    }

    private Mono<Place> addSlug(Place place) {
        String emptySlug = SlugUtils.createEmptySlug(place.getName());

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
    }

    private Mono<ExtendedGroup> updateDominantGroups(Place updatedPlace) {
        return groupsAggregationsRepository.findGroupsComposition(updatedPlace)
                .buffer().singleOrEmpty()
                .flatMap(groups -> {

                    val newDominant = findNewDominant(updatedPlace.getDominantGroupKey(), groups);
                    if (newDominant == null || newDominant.getGroupKey().equals(updatedPlace.getDominantGroupKey())) {
                        return Mono.empty();
                    }

                    return placesRepository
                            .save(updatedPlace.setDominantGroupKey(newDominant.getGroupKey()))
                            .thenReturn(newDominant)
                            .flatMap(eg -> firebaseGroupsService.save(updatedPlace, eg));
                });
    }

    private ExtendedGroup findNewDominant(String currentDominantKey, List<ExtendedGroup> groups) {
        if (groups == null || groups.isEmpty()) return null;
        val res = groups.stream()
                .mapToInt(ExtendedGroup::getValidCheckins).max()
                .stream().mapToObj(
                        maxCheckins -> groups.stream().filter(g -> g.getValidCheckins() == maxCheckins)
                ).flatMap(s -> s).collect(Collectors.toMap(ExtendedGroup::getGroupKey, eg -> eg));

        // should not ever happen, but we don't want exceptions
        if (res.isEmpty()) {
            return null;
        }

        return res.getOrDefault(currentDominantKey, res.values().iterator().next());
    }

    @SneakyThrows
    private Flux<Place> updateTouchedSizes(Place place, double previousSize) {
        val now = Instant.now();

        return placesAggregationsRepository.findPotentiallyTouched(place.getCoordinates(), place.getLastSize() / 1000 + 1)
                .flatMap(potentiallyTouched -> {

                    if (place.getPlaceKey().equals(potentiallyTouched.getPlaceKey())) {
                        return Mono.empty();
                    }

                    double dist = calcDistance(place, potentiallyTouched);
                    double touchedSize = potentiallyTouched.currentSize();
                    double size = place.getLastSize();

                    double newSize;

                    if (dist <= size + touchedSize) {

                        newSize = potentiallyTouched.touched(1);

                        return Mono.just(potentiallyTouched.setLastSize(newSize).setLastSizeChangeTimestamp(now));

                    } else {
                        return Mono.empty();
                    }
                })
                .flatMap(placesRepository::save);
    }

    @SneakyThrows
    private double calcDistance(Place place, Place potentiallyTouched) {
        val geodeticCalc = new GeodeticCalculator();
        geodeticCalc.setStartingGeographicPoint(place.getCoordinates().getX(), place.getCoordinates().getY());
        geodeticCalc.setDestinationGeographicPoint(potentiallyTouched.getCoordinates().getX(), potentiallyTouched.getCoordinates().getY());
        return geodeticCalc.getOrthodromicDistance();
    }
}
