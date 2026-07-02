package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.types.ObjectId;
import org.geotools.referencing.GeodeticCalculator;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;
import to.orbis.v2.backend.exceptions.InvalidCheckinException;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.UnknownUserException;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.repositories.*;
import to.orbis.v2.backend.repositories.queries.PostQuery;
import to.orbis.v2.backend.utils.OrbisBeanUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static reactor.core.publisher.Flux.zip;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    PostsAggregationsRepository postsAggregationsRepository;
    UsersRepository usersRepository;
    GroupsRepository groupsRepository;
    PlacesRepository placesRepository;
    PostsRepository postsRepository;
    CheckinService checkinService;
    GroupsService groupService;
    FollowsRepository followsRepository;
    NotificationsService notificationsService;
    StoriesService storiesService;
    ShortLinksService shortLinksService;
    UsersService usersService;
    FollowsService followsService;
    PlacesInfoService placesInfoService;

    public Mono<ExtendedPost> getPost(String postKey, Optional<String> viewingUserKey) {
        return postsAggregationsRepository.findPosts(
                        PostQuery.builder().postKey(postKey).viewerUserKey(viewingUserKey).build())
                .singleOrEmpty();
    }

    public Flux<ExtendedPost> getPostsForUser(Optional<Instant> from, String userKey, Optional<String> viewingUserKey, EnumSet<PostType> postTypes) {
        return usersService.checkFollower(userKey, viewingUserKey)
                .flatMapMany(_ignored ->
                        postsAggregationsRepository.findPosts(
                                PostQuery.builder().userKey(userKey).viewerUserKey(viewingUserKey).postTypes(new ArrayList<>(postTypes)).from(from).build()));
    }

    public Flux<ExtendedPost> getPostsForPlace(Optional<Instant> from, String placeKey, Optional<String> viewingUserKey, EnumSet<PostType> types, boolean dedup) {
        return postsAggregationsRepository.findPosts(
                PostQuery.builder().placeKey(placeKey).from(from).dedup(dedup).viewerUserKey(viewingUserKey).postTypes(new ArrayList<>(types)).build());
    }

    public Flux<ExtendedPost> getPostsForGroup(Optional<Instant> from, String groupKey, Optional<String> viewingUserKey, EnumSet<PostType> postTypes, boolean dedup) {
        return postsAggregationsRepository.findPosts(
                PostQuery.builder().groupKey(groupKey).dedup(dedup).viewerUserKey(viewingUserKey).postTypes(new ArrayList<>(postTypes)).from(from).build());
    }

    public Mono<ExtendedPost> createPost(Post post, String userKey, boolean checkin) {

        return usersRepository.findOneByUserKey(userKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("User not found")))
                .flatMap(user -> findGroupOptional(post)
                        .flatMap(groupOptional -> findPlaceOptional(post)
                                .flatMap(placeOptional -> doPost(post.setUserKey(userKey), groupOptional, placeOptional, checkin))))
                .flatMap(saved -> postsAggregationsRepository.findPosts(PostQuery.builder().postKey(saved.getPostKey()).viewerUserKey(userKey).build()).singleOrEmpty())
                .map(ep -> {
                    /* TODO: properly fix notifications */
                    notificationsService
                            .notifyPostCreated(ep)
                            .publishOn(Schedulers.boundedElastic())
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe(_unused -> {
                            }, error -> log.error("Failed to notify subscribed user", error));

                    return ep;
                });
    }

    public Mono<Post> doPost(Post post, Optional<Group> groupOptional, Optional<Place> placeOptional, boolean checkin) {
        return validatePost(post, groupOptional, placeOptional, checkin)
                .then(Mono.defer(() -> findCityForPost(post)
                        .flatMap(postNew -> doPostAfterValidation(postNew, groupOptional, placeOptional, checkin))));
    }

    private Mono<Void> validatePost(Post post, Optional<Group> groupOptional, Optional<Place> placeOptional, boolean checkin) {
        boolean banned = groupOptional.map(g -> g.getBanned().contains(post.getUserKey())).orElse(false);

        if (banned) {
            return Mono.error(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You can not post to this group"));
        }

        if (checkin || post.getType() == PostType.CHECK_IN) {
            if (groupOptional.isEmpty() || placeOptional.isEmpty()) {
                return Mono.error(() -> new InvalidCheckinException("Both place and group need to be selected for checkin"));
            }

            if (!withinLimit(post.getCoordinates(), placeOptional.get().getCoordinates())) {
                return Mono.error(() -> new InvalidCheckinException("You must be within 1km from the place to checkin"));
            }
        }

        return Mono.empty();
    }

    private Mono<Post> doPostAfterValidation(Post post, Optional<Group> groupOptional, Optional<Place> placeOptional, boolean checkin) {
        return zip(
                doCheckinPost(post, checkin)
                        .map(Optional::of)
                        .switchIfEmpty(Mono.just(Optional.empty())),
                doRegularPost(post)
                        .map(Optional::of)
                        .switchIfEmpty(Mono.just(Optional.empty()))
        ).singleOrEmpty()
                .map(t -> {
                    val checkinPost = t.getT1();
                    val regularPost = t.getT2();

                    return regularPost.or(() -> checkinPost)
                            // should never happen
                            .orElseThrow(() -> new InvalidCheckinException("Neither regular nor checkin post didn't succeed"));
                }).flatMap(saved -> groupOptional.map(group -> {
                    if (!group.getMembers().contains(saved.getUserKey())) {
                        return groupService.addMember(group.getGroupKey(), saved.getUserKey()).thenReturn(saved);
                    } else {
                        return Mono.just(saved);
                    }
                }).orElse(Mono.just(saved)));
    }

    public Mono<Post> findCityForPost(Post post) {
        return placesInfoService.findCityByCoordinates(post.getCoordinates())
                .map(city -> {
                    post.setCity(city);
                    return post;
                })
                .onErrorReturn(post);
    }

    private Mono<Post> doRegularPost(Post post) {

        // never regular post CHECKIN posts
        if (post.getType() == PostType.CHECK_IN) {
            return Mono.empty();
        }

        post.setId(new ObjectId());
        post.setPostKey(post.getId().toHexString());

        return shortLinksService.generateShortPostLink(post)
                .flatMap(sl -> {
                    post.setShareLink(sl.getShortLink());
                    post.setFullShareLink(sl.getFullLink());

                    return postsRepository.save(post)
                            .map(savedPost -> {
                                if (savedPost.getGroupKey() == null || savedPost.getGroupKey().isEmpty()
                                        || (savedPost.getType() != PostType.IMAGE && savedPost.getType() != PostType.VIDEO)) {
                                    return savedPost;
                                }

                                storiesService.saveToStory(savedPost)
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .publishOn(Schedulers.boundedElastic())
                                        .subscribe(_ignored -> {},
                                                error -> log.error("Failed to update story with post {} for group {}",
                                                        savedPost.getPostKey(), savedPost.getGroupKey(), error));

                                return savedPost;
                            });
                });
    }

    private Mono<Post> doCheckinPost(Post post, boolean checkin) {

        // never checkin post regular posts
        if (post.getType() != PostType.CHECK_IN && !checkin) {
            return Mono.empty();
        }

        val checkinPost = post.getType() == PostType.CHECK_IN
                ? post
                : postToCheckinPost(post);

        checkinPost.setId(new ObjectId());
        checkinPost.setPostKey(checkinPost.getId().toHexString());
        checkinPost.setTimestamp(checkinPost.getTimestamp().minus(1, ChronoUnit.SECONDS));

        return postsRepository.save(checkinPost)
                .flatMap(saved -> checkinService.checkinAndReturnPolygonCoordinateKey(saved.getGroupKey(), saved.getPlaceKey(), saved.getUserKey())
                        .flatMap(checkInPolygonCoordinateKey -> {
                            saved.setCheckInPolygonCoordinateKey(checkInPolygonCoordinateKey);
                            return postsRepository.save(saved)
                                            .map(_ignored -> followsService.followPlace(saved.getPlaceKey(), saved.getUserKey()));
                        })
                        .thenReturn(saved));
    }

    private Post postToCheckinPost(Post post) {
        val res = new Post();
        res.setCoordinates(post.getCoordinates());
        res.setGroupKey(post.getGroupKey());
        res.setUserKey(post.getUserKey());
        res.setPlaceKey(post.getPlaceKey());
        res.setType(PostType.CHECK_IN);
        res.setTimestamp(post.getTimestamp());
        return res;
    }

    private Mono<Optional<Place>> findPlaceOptional(Post post) {
        return Mono.justOrEmpty(post.getPlaceKey())
                .flatMap(placeKey -> placesRepository.findOneByPlaceKey(placeKey)
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Place not found"))))
                .map(Optional::of)
                .switchIfEmpty(Mono.just(Optional.empty()));
    }

    private Mono<Optional<Group>> findGroupOptional(Post post) {
        return Mono.justOrEmpty(post.getGroupKey())
                .flatMap(groupKey -> groupsRepository.findOneByGroupKeyAndDeletedFalse(groupKey)
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Group not found"))))
                .map(Optional::of)
                .switchIfEmpty(Mono.just(Optional.empty()));
    }

    private static final double MAX_CHECKIN_DISTANCE = 1000.0;

    @SneakyThrows
    private boolean withinLimit(GeoJsonPoint postCoordinates, GeoJsonPoint placeCoordinates) {
        val calc = new GeodeticCalculator();
        calc.setStartingGeographicPoint(postCoordinates.getX(), postCoordinates.getY());
        calc.setDestinationGeographicPoint(placeCoordinates.getX(), placeCoordinates.getY());
        val res = calc.getOrthodromicDistance();
        return res < MAX_CHECKIN_DISTANCE;
    }

    public Flux<PostComponent> groupSliders(Flux<ExtendedPost> posts, boolean skipDupCheckins, int pageSize) {
        return formUnboundedTuples(posts, skipDupCheckins)
                .take(pageSize / 10)
                .flatMap(acc -> Flux.merge(
                        Flux.fromIterable(acc.getT1()).take(30).buffer().map(PostComponent::slider),
                        Flux.fromIterable(acc.getT2()).map(PostComponent::post)
                ));
    }

    public Flux<PostComponent> groupSlidersSkewed(Flux<ExtendedPost> posts, Flux<ExtendedPost> checkins, boolean skipDupCheckins, int pageSize, boolean compareHeads, Optional<Integer> sliderShift) {

        val limitedPosts = posts.take(pageSize, true);

        return (compareHeads
                ? groupingSlidingMerge(limitedPosts, checkins, skipDupCheckins)
                : alternatingMerge(limitedPosts, checkins, skipDupCheckins))
                .flatMap(t -> {
                    if (t.getT1().isEmpty() && t.getT2().isEmpty()) return Flux.empty();

                    boolean inverse = (!t.getT1().isEmpty() && t.getT1().get(0).getType() == PostType.CHECK_IN)
                            || (!t.getT2().isEmpty() && t.getT2().get(0).getType() != PostType.CHECK_IN);

                    val ps = (!inverse ? t.getT1() : t.getT2()).stream().map(PostComponent::post);
                    val cs = Optional.of(!inverse ? t.getT2() : t.getT1())
                            .filter(l -> !l.isEmpty())
                            .map(it -> PostComponent.slider(it));


                    int shift = sliderShift.filter(_ignore -> !compareHeads).orElse(inverse ? 10 : 0);

                    val postsFlux = Flux.fromStream(ps).cache();
                    return Flux.mergeSequential(
                            postsFlux.take(shift),
                            Flux.fromStream(cs.stream()),
                            postsFlux.skip(shift)
                    );

                })
                .take(pageSize, true);
    }

    private Flux<Tuple2<List<ExtendedPost>, List<ExtendedPost>>> alternatingMerge(Flux<ExtendedPost> posts, Flux<ExtendedPost> checkins, boolean skipDupCheckins) {
        return zip(appendEmpty(posts.buffer(10)), appendEmpty(dedup(checkins, skipDupCheckins)))
                .takeWhile(t -> !t.getT1().isEmpty() || !t.getT2().isEmpty());
    }

    private Flux<Tuple2<List<ExtendedPost>, List<ExtendedPost>>> groupingSlidingMerge(Flux<ExtendedPost> posts, Flux<ExtendedPost> checkins, boolean skipDupCheckins) {
        return posts
                .switchOnFirst((e1, sf1) -> dedup(checkins, skipDupCheckins)
                        .switchOnFirst((e2, sf2) -> {
                                    if (e1.isOnComplete()) return zip(appendEmpty(emptyListFlux()), sf2);
                                    if (e2.isOnComplete()) return zip(sf1.buffer(10), appendEmpty(emptyListFlux()));

                                    if (e1.isOnError()) return zip(sf1.buffer(10), appendEmpty(emptyListFlux()));
                                    if (e2.isOnError()) return zip(appendEmpty(emptyListFlux()), sf2);

                                    val p = e1.get();
                                    val c = e2.get();

                                    val shared1 = sf1.cache();
                                    val shared2 = sf2.cache()/*.log("shared2")*/;
                                    val head = isAfter(p, c) ? zip(shared1.takeWhile(ce -> isAfter(ce, c)).buffer(10), appendEmpty(emptyListFlux()))
                                            : zip(appendEmpty(emptyListFlux()), shared2.take(1));
                                    val tail = isAfter(p, c)
                                            ? zip(appendEmpty(shared1.skipWhile(ce -> isAfter(ce, c)).buffer(10)), appendEmpty(shared2))
                                            : zip(appendEmpty(shared2.skip(1)), appendEmpty(shared1.buffer(10)));
                                    return Flux.mergeSequential(head, tail);
                                }
                        ))
                .takeWhile(t -> !t.getT1().isEmpty() || !t.getT2().isEmpty());
    }

    private Flux<List<ExtendedPost>> emptyListFlux() {
        return Flux.empty();
    }

    private boolean isAfter(ExtendedPost p, List<ExtendedPost> c) {
        if (p == null || p.getTimestamp() == null) return false;
        if (c == null || c.isEmpty() || c.get(0).getTimestamp() == null) return true;
        return p.getTimestamp().isAfter(c.get(0).getTimestamp());
    }

    private <T> Flux<List<T>> appendEmpty(Flux<List<T>> in) {
        return Flux.mergeSequential(in, Flux.just(Collections.<T>emptyList()).cache().repeat());
    }


    private Flux<List<ExtendedPost>> dedup(Flux<ExtendedPost> checkins, boolean skipDupCheckins) {
        return (skipDupCheckins
                ? checkins.distinct(Post::getUserKey).buffer(10)
                : checkins.buffer(10)).take(10, true);
    }

    private Tuple2<HashSet<String>, ArrayList<ExtendedPost>> freshTuple() {
        return Tuples.of(
                new HashSet<String>(),
                new ArrayList<ExtendedPost>());
    }

    private <T> List<T> nl() {
        return new ArrayList<>();
    }

    private <K, T> Map<K, T> nm() {
        return new LinkedHashMap<>();
    }

    private <T> List<T> append(List<T> posts, T post) {
        posts.add(post);
        return posts;
    }

    private <K, T> Map<K, T> append(Map<K, T> posts, T post, Function<T, K> keyFunc) {
        posts.putIfAbsent(keyFunc.apply(post), post);
        return posts;
    }

    private <K, T> Map<K, T> append(Map<K, T> posts, T post, Function<T, K> keyFunc, int limit) {
        if (posts.size() < limit) {
            posts.putIfAbsent(keyFunc.apply(post), post);
        }
        return posts;
    }

    private Flux<Tuple4<List<ExtendedPost>, List<ExtendedPost>, Boolean, Instant>> formUnboundedTuples(Flux<ExtendedPost> posts, boolean skipDupCheckins) {
        return scanFlux(posts, skipDupCheckins)
                .window(2, 1)
                .flatMap(w -> w.buffer().map(wc -> {
                    if (wc.size() == 1) {
                        return wc.get(0).mapT3(t -> true);
                    }
                    return wc.get(0);
                }))
                .filter(Tuple4::getT3)
                .map(t -> t.mapT1(m -> new ArrayList<>(m.values())));
    }

    private Flux<Tuple4<Map<String, ExtendedPost>, List<ExtendedPost>, Boolean, Instant>> scanFlux(Flux<ExtendedPost> posts, boolean skipDupCheckins) {
        final Map<String, ExtendedPost> checkins = nm();
        final List<ExtendedPost> nonCheckins = nl();
        return posts
                .window(2, 1)
                .flatMap(f -> f.buffer().map(list -> {
                    if (list.size() == 1) {
                        return Tuples.of(list.get(0), false);
                    }
                    return Tuples.of(list.get(0), list.get(1).getType() == PostType.CHECK_IN);
                }))
                .scan(Tuples.of(checkins, nonCheckins, false, Instant.now(), false), (c, a) -> {
                    val current = a.getT1();
                    val nextIsCheckIn = a.getT2();
                    val seenCheckin = c.getT5() || current.getType() == PostType.CHECK_IN;
                    final Function<ExtendedPost, String> dedupFunc = skipDupCheckins ? Post::getUserKey : Post::getPostKey;

                    if (c.getT3()) {
                        if (current.getType() == PostType.CHECK_IN) {
                            return Tuples.of(append(nm(), current, dedupFunc), nl(), false, current.getTimestamp(), seenCheckin);
                        }
                        return Tuples.of(nm(), append(nl(), current), false, current.getTimestamp(), seenCheckin);
                    }
                    if (current.getType() == PostType.CHECK_IN) {
                        return Tuples.of(append(c.getT1(), current, dedupFunc, 30), c.getT2(), false, current.getTimestamp(), seenCheckin);
                    }
                    List<ExtendedPost> res = append(c.getT2(), current);
                    if (res.size() == 10 || (!seenCheckin && nextIsCheckIn)) {
                        return Tuples.of(c.getT1(), res, true, current.getTimestamp(), seenCheckin);
                    }

                    return Tuples.of(c.getT1(), res, false, current.getTimestamp(), seenCheckin);
                })
                // make typechecker happy
                .map(t -> (Tuple4<Map<String, ExtendedPost>, List<ExtendedPost>, Boolean, Instant>) t);
    }

    public Flux<ExtendedPost> getPosts(Optional<Instant> from, Optional<String> viewingUserKey, EnumSet<PostType> types, boolean dedup) {
        return postsAggregationsRepository
                .findPosts(PostQuery
                        .builder()
                        .from(from)
                        .viewerUserKey(viewingUserKey)
                        .dedup(dedup)
                        .postTypes(new ArrayList<>(types))
                        .build());
    }

    public Flux<ExtendedPost> getPostsNear(Optional<NextPage> from, GeoJsonPoint point, double distance, Optional<String> viewingUserKey, EnumSet<PostType> types, boolean dedup) {
        val typesList = new ArrayList<>(types);
        return Flux.mergeSequential(from.map(
                                        // if next page was not switched to distance based
                                        np -> (np.getDist() == null || np.getDist() < distance - 0.001)
                                                // page based on timestamps
                                                ? postsAggregationsRepository.findPosts(
                                                        PostQuery.builder()
                                                                .postTypes(typesList)
                                                                .dedup(dedup)
                                                                .viewerUserKey(viewingUserKey)
                                                                .from(from.map(NextPage::getTimestamp))
                                                                .near(point, distance)
                                                                .build())
                                                // other page based on distances
                                                : postsAggregationsRepository.findPosts(
                                                        PostQuery.builder()
                                                                .further(point, np.getDist())
                                                                .postTypes(typesList).dedup(dedup)
                                                                .viewerUserKey(viewingUserKey)
                                                                .after(Instant.now().minus(30, ChronoUnit.DAYS))
                                                                .build()))
                                // first page
                                .orElse(postsAggregationsRepository.findPosts(
                                        PostQuery.builder()
                                                .from(Optional.empty())
                                                .postTypes(typesList)
                                                .viewerUserKey(viewingUserKey)
                                                .dedup(dedup)
                                                .near(point, distance)
                                                .build()))
                        // if paging based on timestamps came out empty - try to find posts further away effectively switching to
                        // distance based paged
                        , postsAggregationsRepository
                                .findPosts(PostQuery.builder()
                                        .postTypes(typesList)
                                        .dedup(dedup)
                                        .viewerUserKey(viewingUserKey)
                                        .further(point, distance)
                                        .after(Instant.now().minus(30, ChronoUnit.DAYS))
                                        .build())

                        // and if time limited out-of-25km comes out empty - get unbounded query
                        , postsAggregationsRepository
                                .findPosts(PostQuery.builder()
                                        .dedup(dedup)
                                        .further(point, distance)
                                        .viewerUserKey(viewingUserKey)
                                        .postTypes(typesList)
                                        .build()))
                .distinct(Post::getPostKey);
    }

    public Flux<ExtendedPost> getPostsByCity(
            Optional<Instant> from, GeoJsonPoint point, Optional<String> viewingUserKey, EnumSet<PostType> types, boolean dedup) {
        return placesInfoService.findCityByCoordinates(point)
                .flatMapMany(city ->
                    postsAggregationsRepository
                            .findPosts(PostQuery
                                    .builder()
                                    .from(from)
                                    .city(city)
                                    .viewerUserKey(viewingUserKey)
                                    .dedup(dedup)
                                    .postTypes(new ArrayList<>(types))
                                    .build())
                );
    }

    public Flux<ExtendedPost> getPostsNews(Optional<Instant> from, String userKey, EnumSet<PostType> postTypes, boolean dedup) {
        return followsRepository.findAllByFollowerKeyAndAcceptedTrue(userKey)/*.log("findfollow")*/
                .buffer()
                .switchIfEmpty(Mono.just(Collections.emptyList()))
                .flatMap(follows -> {
                    val withMyself = new ArrayList<>(follows);
                    val selfFollow = new Follow();
                    selfFollow.setFollowerKey(userKey);
                    selfFollow.setUserKey(userKey);
                    selfFollow.setAccepted(true);
                    withMyself.add(selfFollow);
                    return postsAggregationsRepository.findUserFeed(from, withMyself, Optional.of(userKey), postTypes, dedup)/*.log("findnews")*/;
                });
    }

    public Flux<ExtendedPost> getPlaceEvents(String placeKey, boolean pastEvents, int page, int size, Optional<String> viewingUserKey) {
        return postsAggregationsRepository.findPosts(
                PostQuery
                        .builder()
                        .placeKey(placeKey)
                        .page(page)
                        .limit(size)
                        .postTypes(List.of(PostType.EVENT))
                        .eventStarts(Instant.now())
                        .viewerUserKey(viewingUserKey)
                        .pastEvents(pastEvents)
                        .build());
    }

    public Flux<ExtendedPost> getGroupEvents(String groupKey, boolean pastEvents, int page, int size, Optional<String> viewingUserKey) {
        return postsAggregationsRepository.findPosts(
                PostQuery
                        .builder()
                        .groupKey(groupKey)
                        .page(page)
                        .limit(size)
                        .postTypes(List.of(PostType.EVENT))
                        .eventStarts(Instant.now())
                        .pastEvents(pastEvents)
                        .viewerUserKey(viewingUserKey)
                        .build());
    }

    public Mono<Void> storySeen(String postKey, String userKey) {
        return postsAggregationsRepository.storySeen(postKey, userKey);
    }

    public Mono<ExtendedPost> unlikePost(String postKey, String userKey) {
        return postsAggregationsRepository.unlikePost(postKey, userKey)
                .flatMap(_ignored -> getPost(postKey, Optional.of(userKey)));
    }

    public Mono<ExtendedPost> likePost(String postKey, String userKey) {
        return postsAggregationsRepository.likePost(postKey, userKey)
                .flatMap(_ignored -> getPost(postKey, Optional.of(userKey)));
    }

    public Mono<Void> deletePost(String postKey, String userKey) {
        return usersRepository.findOneByUserKey(userKey)
                .switchIfEmpty(Mono.error(UnknownUserException::new))
                .flatMap(u -> postsRepository.findOneByPostKey(postKey)
                        .filter(p -> !p.isDeleted())
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post not found or was already deleted")))
                        .flatMap(p -> (
                                p.getGroupKey() == null
                                        ? Mono.just(Optional.<Group>empty())
                                        : groupsRepository.findByNameAndDeletedFalse(p.getGroupKey()).map(Optional::of)
                                        .switchIfEmpty(Mono.just(Optional.empty())))
                                .flatMap(mg -> {
                                    if (userKey.equals(p.getUserKey())
                                            || u.isSuperAdmin()
                                            || mg.filter(g -> g.getAdmins().contains(userKey)).isPresent()) {
                                        return postsAggregationsRepository.deletePost(postKey)
                                                .flatMap(_ignored -> storiesService.deletePostFromStory(postKey));
                                    } else {
                                        return Mono.error(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You can not delete this post"));
                                    }
                                }))).then();
    }

    public Mono<ExtendedPost> updatePost(String postKey, Post updatedPostData, String userKey) {
        return usersRepository.findOneByUserKey(userKey)
                .switchIfEmpty(Mono.error(UnknownUserException::new))
                .flatMap(u -> postsRepository.findOneByPostKey(postKey)
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post not found")))
                        .flatMap(currentPostData -> checkPermissions(u, currentPostData, updatedPostData))
                        .flatMap(currentPostData -> updatePostInternal(u, currentPostData, updatedPostData)));
    }

    private Mono<Post> checkPermissions(User user, Post currentPostData, Post updatedPostData) {
        if (user.getUserKey().equals(currentPostData.getUserKey())
                || (user.isSuperAdmin())) {
            return Mono.just(currentPostData);
        }

        final Supplier<Throwable> throwableSupplier = () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You can not edit other user's post");

        if (currentPostData.getGroupKey() != null && !currentPostData.getGroupKey().isBlank()) {
            return groupsRepository.findOneByGroupKeyAndDeletedFalse(currentPostData.getGroupKey())
                    .flatMap(group -> {
                        if (group.getAdmins().contains(user.getUserKey())) {
                            return Mono.just(currentPostData);
                        } else {
                            return Mono.error(throwableSupplier);
                        }
                    });
        }

        return Mono.error(throwableSupplier);
    }

    private Mono<ExtendedPost> updatePostInternal(User user, Post currentPostData, Post updatedPostData) {

        val post = updateFields(currentPostData, updatedPostData);

        return postsRepository.save(post)
                .flatMap(p -> this.getPost(p.getPostKey(), Optional.of(user.getUserKey())));
    }

    private Post updateFields(Post existingPost, Post incomingPost) {

        OrbisBeanUtils.copyNotNullPropertiesSkipping(Post.class, existingPost, incomingPost,
                Post.Fields.postKey, Post.Fields.groupKey, Post.Fields.deleted, Post.Fields.liked,
                Post.Fields.type, Post.Fields.userKey, Entity.Fields.id);

        return existingPost;
    }

    public Mono<String> sharePost(String postKey) {
        return postsRepository.findOneByPostKey(postKey)
                .flatMap(p -> (p.getShareLink() == null || p.getShareLink().isBlank())
                        ? shortLinksService.generateShortPostLink(p)
                        .flatMap(sl -> postsRepository.save(p.setShareLink(sl.getShortLink())))
                        .map(Post::getShareLink)
                        : Mono.just(p.getShareLink()));
    }

    public Mono<String> reportPost(String postKey, String reason, Optional<String> userKey) {
        return postsAggregationsRepository.findPosts(PostQuery.builder().postKey(postKey).viewerUserKey(userKey).build())
                .singleOrEmpty()
                .flatMap(p -> userKey.map(usersRepository::findOneByUserKey).orElse(Mono.empty()).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
                        .flatMap(u -> {
                            if (p.getReported() != null && p.getReported()) {
                                return Mono.just("Post is already reported");
                            }

                            return postsAggregationsRepository.setReported(p.getPostKey(), reason)
                                    .flatMap(_ignored -> sendReport(p, reason, u));
                        }));
    }

    private Mono<String> sendReport(ExtendedPost post, String reason, Optional<User> user) {
        Mono<String> shortLink;
        if (post.getShareLink() == null || post.getShareLink().isEmpty()) {
            shortLink = shortLinksService.generateShortPostLink(post)
                    .flatMap(link -> Mono.just(link.getShortLink()));
        } else {
            shortLink = Mono.just(post.getShareLink());
        }

        return shortLink.flatMap(sl -> {
            val title = String.format("Post '%s' was reported", post.getTitle());

            var body = "Reason: " + reason + "\n" +
                    "Post details:\n";

            body += "Open in app: " + sl + "\n";
            body += "Type: " + post.getType() + "\n";
            body += "Details: " + post.getDetails() + "\n";
            body += "\n";

            body += "Posted by: \n";
            body += "User name: " + post.getUser().getDisplayName() + "\n";
            body += "\n";

            if (post.getGroupKey() != null) {
                body += "Posted in group:\n";
                body += "     " + post.getGroup().getName() + "\n";
                body += "\n";
            }

            if (post.getPlaceKey() != null) {
                body += "Posted in place: \n";
                body += "    " + post.getPlace().getName() + "( " + post.getPlace().getAddress() + " )\n";
                body += "\n";
            }

            if (user.isPresent()) {
                body += "User reported: \n";
                body += "    " + user.get().getDisplayName() + "\n";
            }
            return notificationsService.reportPost(title, body, post, user).map(_ignored -> "Post was reported");
        });
    }
}
