package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.UnknownUserException;
import to.orbis.v2.backend.mappers.NextPageMapper;
import to.orbis.v2.backend.mappers.PostMapper;
import to.orbis.v2.backend.mappers.StoryMapper;
import to.orbis.v2.backend.models.PostComponentType;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.dto.FeedDto;
import to.orbis.v2.backend.models.dto.PostComponentDto;
import to.orbis.v2.backend.models.dto.PostDto;
import to.orbis.v2.backend.models.dto.StoryDto;
import to.orbis.v2.backend.models.entity.NextPage;
import to.orbis.v2.backend.services.*;
import to.orbis.v2.backend.utils.ControllerUtils;
import to.orbis.v2.backend.utils.GeoHashUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import to.orbis.v2.backend.exceptions.ForwardToNodeJsException;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

import static to.orbis.v2.backend.utils.ControllerUtils.maybeAuthorized;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/feed")
public class FeedsController {

    public static final EnumSet<PostType> NON_CHECKIN = EnumSet.complementOf(EnumSet.of(PostType.CHECK_IN));
    public static final EnumSet<PostType> CHECK_IN = EnumSet.of(PostType.CHECK_IN);
    PostMapper postMapper;
    PostService postService;
    NextPageMapper nextPageMapper;
    StoryMapper storyMapper;
    StoriesService storiesService;
    UsersService usersService;
    PlacesService placesService;
    GroupsService groupsService;
    ReactiveMongoTemplate mongoTemplate;

    @GetMapping("/all")
    public Mono<FeedDto> feedAll(@RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication) {
        val np = nextPage.map(nextPageMapper::b64StringToNextPage);
        val checkinsFrom = np.map(NextPage::getChekinsTimestamp);

        return postService.groupSlidersSkewed(
                postService.getPosts(np.map(NextPage::getTimestamp), maybeAuthorized(authentication), NON_CHECKIN,
                        false),
                postService.getPosts(checkinsFrom, maybeAuthorized(authentication), CHECK_IN, true),
                false, size, checkinsFrom.isEmpty(), np.map(NextPage::getSliderShift))
                .map(postMapper::postComponentToPostComponentDto)
                .collectList()
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                .map(postComponentDtos -> toFeedDto(np, postComponentDtos))
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    private FeedDto toFeedDto(Optional<NextPage> currentNextPage, List<PostComponentDto> postComponentDtos) {
        val regroupedFeed = regroup(postComponentDtos);
        return new FeedDto().setContent(regroupedFeed)
                .setNextPage(buildNextPage(currentNextPage, regroupedFeed));
    }

    private String buildNextPage(Optional<NextPage> currentNextPage, List<PostComponentDto> postComponentDtos) {
        if (postComponentDtos.isEmpty())
            return null;

        boolean seenPost = false;
        boolean seenSlider = false;
        val nextPage = new NextPage();
        for (int i = 0; i < postComponentDtos.size(); i++) {
            int idx = postComponentDtos.size() - i - 1;
            val pc = postComponentDtos.get(idx);

            if (pc.getType() == PostComponentType.SLIDER && !seenSlider) {
                seenSlider = true;
                nextPage.setSliderShift(10 - i);
                if (nextPage.getSliderShift() > 10 || nextPage.getSliderShift() < 0)
                    nextPage.setSliderShift(0);
                nextPage.setChekinsDistance(pc.getSlider().get(pc.getSlider().size() - 1).getDist());
                if (pc.getSlider().size() == 10) {
                    nextPage.setChekinsTimestamp(pc.getSlider().get(pc.getSlider().size() - 1).getTimestamp());
                } else {
                    nextPage.setChekinsTimestamp(pc.getSlider().stream()
                            .map(p -> Optional.ofNullable(p.getLastSeen()).orElse(p.getTimestamp()))
                            .min(Instant::compareTo).orElse(Instant.EPOCH));
                }
            }

            if (pc.getType() == PostComponentType.POST && !seenPost) {
                seenPost = true;
                nextPage.setDist(pc.getPost().getDist());
                nextPage.setTimestamp(pc.getPost().getTimestamp());
            }
            if (seenPost && seenSlider)
                break;
        }

        if (currentNextPage.isPresent()) {
            if (!seenPost) {
                nextPage.setTimestamp(currentNextPage.get().getTimestamp());
            }

            if (!seenSlider) {
                nextPage.setChekinsTimestamp(currentNextPage.get().getChekinsTimestamp());
            }
        }

        return nextPageMapper.nextPageToB64String(nextPage);
    }

    List<PostComponentDto> regroup(List<PostComponentDto> postComponentDtos) {
        int currentSlider = -1;

        val res = new ArrayList<PostComponentDto>();

        for (final PostComponentDto c : postComponentDtos) {
            if (c.getType() != PostComponentType.SLIDER) {
                res.add(c);
                continue;
            }

            if (currentSlider == -1) {
                currentSlider = res.size();
                res.add(c);
                continue;
            }

            if (res.size() - currentSlider - 1 < 10) {
                res.get(currentSlider).getSlider().addAll(c.getSlider());
            } else {
                currentSlider = res.size();
                res.add(c);
            }
        }

        return res;
    }

    @GetMapping("/city")
    public Mono<FeedDto> feedCity(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication auth) {
        log.info("Incoming request: /feed/city (javaProxied={})", javaProxied);
        log.info("feedCity: longitude={}, latitude={}", longitude, latitude);

        return getNetworkEventId(latitude, longitude, "posts", javaProxied)
                .flatMap(eventId -> Mono.<FeedDto>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(Mono.defer(() -> {
                    final Optional<NextPage> np = nextPage.map(nextPageMapper::b64StringToNextPage);
                    final Optional<NextPage> checkinsNextPage = np.isEmpty() || np.get().getPageNumber() == null
                            ? Optional.of(new NextPage().setPageNumber(0))
                            : np;
                    return postService.groupSlidersSkewed(
                            postService.getPostsByCity(
                                    np.map(NextPage::getTimestamp),
                                    new GeoJsonPoint(longitude, latitude),
                                    maybeAuthorized(auth),
                                    NON_CHECKIN,
                                    false),
                            postService.getPostsByCity(
                                    np.map(NextPage::getChekinsTimestamp),
                                    new GeoJsonPoint(longitude, latitude),
                                    maybeAuthorized(auth),
                                    CHECK_IN,
                                    false),
                            true, size, np.isEmpty(), np.map(NextPage::getSliderShift))
                            .map(postMapper::postComponentToPostComponentDto)
                            .collectList()
                            .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                            .map(postComponentDtos -> toFeedDto(checkinsNextPage, postComponentDtos))
                            .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
                }));
    }

    @GetMapping("/near")
    public Mono<FeedDto> feedNear(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(required = false, defaultValue = "10.0") double distance,
            @RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication auth) {
        log.info("Incoming request: /feed/near (javaProxied={})", javaProxied);
        log.info("feedNear: longitude={}, latitude={}, distance={}", longitude, latitude, distance);

        return getNetworkEventId(latitude, longitude, "posts", javaProxied)
                .flatMap(eventId -> Mono.<FeedDto>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(Mono.defer(() -> {
                    final Optional<NextPage> np = nextPage.map(nextPageMapper::b64StringToNextPage);
                    final Optional<NextPage> checkinsNextPage = np
                            .filter(p -> p.getChekinsDistance() != null && p.getChekinsTimestamp() != null)
                            .map(p -> new NextPage().setDist(p.getChekinsDistance()).setTimestamp(p.getChekinsTimestamp()));
                    return postService.groupSlidersSkewed(
                            postService.getPostsNear(
                                    np,
                                    new GeoJsonPoint(longitude, latitude),
                                    distance,
                                    maybeAuthorized(auth),
                                    NON_CHECKIN,
                                    false),
                            postService.getPostsNear(
                                    checkinsNextPage,
                                    new GeoJsonPoint(longitude, latitude),
                                    distance,
                                    maybeAuthorized(auth),
                                    CHECK_IN,
                                    false),
                            true, size, checkinsNextPage.isEmpty(), np.map(NextPage::getSliderShift))
                            .map(postMapper::postComponentToPostComponentDto)
                            .collectList()
                            .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                            .map(postComponentDtos -> toFeedDto(np, postComponentDtos))
                            .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
                }));
    }

    @GetMapping("/city/stories")
    public Mono<List<StoryDto>> storiesNear(
            @RequestParam @Min(-180) @Max(180) double longitude,
            @RequestParam @Min(-90) @Max(90) double latitude,
            @RequestParam(required = false, defaultValue = "false") boolean seen,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication auth) {
        return storiesService
                .getCityStories(new GeoJsonPoint(longitude, latitude), seen, ControllerUtils.maybeAuthorized(auth),
                        PageRequest.of(page, size))
                .map(storyMapper::storyToStoryDto)
                .collectList()
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @GetMapping("/near/stories")
    public Mono<List<StoryDto>> storiesNear(
            @RequestParam @Min(-180) @Max(180) double longitude,
            @RequestParam @Min(-90) @Max(90) double latitude,
            @RequestParam(required = false, defaultValue = "1000.0") double distance,
            @RequestParam(required = false, defaultValue = "false") boolean seen,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication auth) {
        return storiesService
                .getNearStories(new GeoJsonPoint(longitude, latitude), distance, seen,
                        ControllerUtils.maybeAuthorized(auth), PageRequest.of(page, size))
                .map(storyMapper::storyToStoryDto)
                .collectList()
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @GetMapping("/news")
    @PreAuthorize("isAuthenticated")
    public Mono<FeedDto> feedNews(
            @RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication) {

        val np = nextPage.map(nextPageMapper::b64StringToNextPage);
        val checkinsFrom = np.map(NextPage::getChekinsTimestamp);

        return postService.groupSlidersSkewed(
                postService.getPostsNews(np.map(NextPage::getTimestamp), authentication.getName(), NON_CHECKIN, false),
                postService.getPostsNews(checkinsFrom, authentication.getName(), CHECK_IN, true),
                false, size, checkinsFrom.isEmpty(), np.map(NextPage::getSliderShift))
                .name("feed").tag("op", "news").metrics()
                .map(postMapper::postComponentToPostComponentDto)
                .collectList()
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                .map(postComponentDtos -> toFeedDto(np, postComponentDtos))
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @GetMapping("/news/stories")
    @PreAuthorize("isAuthenticated")
    public Mono<List<StoryDto>> storiesNews(
            @RequestParam(required = false, defaultValue = "false") boolean seen,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication) {
        return storiesService.getNewsStories(seen, authentication.getName(), PageRequest.of(page, size))
                .map(storyMapper::storyToStoryDto)
                .collectList()
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @PutMapping("/stories/{postKey}/seen")
    @PreAuthorize("isAuthenticated")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> storySeen(@PathVariable String postKey, Authentication authentication) {
        return postService.storySeen(postKey, authentication.getName());
    }

    @GetMapping("/user/slug/{slug}")
    @PreAuthorize("permitAll")
    public Mono<FeedDto> feedForUser(
            @PathVariable(name = "slug") String slug,
            @RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication authentication) {
        return usersService.getUserBySlug(slug)
                .flatMap(user -> feedForUser(Optional.of(user.getUserKey()), nextPage, size, javaProxied, authentication));
    }

    @GetMapping({ "/user", "/user/{userKey}" })
    @PreAuthorize("permitAll")
    public Mono<FeedDto> feedForUser(
            @PathVariable(name = "userKey") Optional<String> mayBeUserKey,
            @RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication authentication) {
        if (mayBeUserKey.filter(s -> !s.isBlank()).isEmpty() && !authentication.isAuthenticated()) {
            return Mono.error(UnknownUserException::new);
        }

        val np = nextPage.map(nextPageMapper::b64StringToNextPage);
        val checkinsFrom = np.map(NextPage::getChekinsTimestamp);
        
        String key = mayBeUserKey.orElse(authentication.getName());

        return getNetworkEventIdByAuthorHash(key, "posts", javaProxied)
                .flatMap(eventId -> Mono.<FeedDto>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(postService.groupSlidersSkewed(
                        postService.getPostsForUser(np.map(NextPage::getTimestamp),
                                key, maybeAuthorized(authentication), NON_CHECKIN),
                        postService.getPostsForUser(checkinsFrom, key,
                                maybeAuthorized(authentication), CHECK_IN),
                        false, size, checkinsFrom.isEmpty(), np.map(NextPage::getSliderShift))
                        .name("feed").tag("op", "user").metrics()
                        .map(postMapper::postComponentToPostComponentDto)
                        .collectList()
                        .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                        .map(postComponentDtos -> toFeedDto(np, postComponentDtos))
                        .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException())));
    }

    @GetMapping("/place/slug/{slug}")
    @PreAuthorize("permitAll")
    public Mono<FeedDto> feedForPlaceBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication auth) {
        return placesService.getPlaceBySlug(slug)
                .flatMap(place -> feedForPlace(place.getPlaceKey(), nextPage, size, javaProxied, auth));
    }

    @GetMapping({ "/place/{placeKey}" })
    @PreAuthorize("permitAll")
    public Mono<FeedDto> feedForPlace(
            @PathVariable String placeKey,
            @RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication auth) {
        val np = nextPage.map(nextPageMapper::b64StringToNextPage);
        val checkinsFrom = np.map(NextPage::getChekinsTimestamp);

        return getNetworkEventIdByParentHash2(placeKey, "posts", javaProxied)
                .flatMap(eventId -> Mono.<FeedDto>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(postService.groupSlidersSkewed(
                        postService.getPostsForPlace(np.map(NextPage::getTimestamp), placeKey, maybeAuthorized(auth),
                                NON_CHECKIN, false),
                        postService.getPostsForPlace(checkinsFrom, placeKey, maybeAuthorized(auth), CHECK_IN, true),
                        false, size, checkinsFrom.isEmpty(), np.map(NextPage::getSliderShift))
                        .name("feed").tag("op", "place")
                        .metrics()
                        .map(postMapper::postComponentToPostComponentDto)
                        .collectList()
                        .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                        .map(postComponentDtos -> toFeedDto(np, postComponentDtos))
                        .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException())));
    }

    @GetMapping("/group/slug/{slug}")
    @PreAuthorize("permitAll")
    public Mono<FeedDto> feedForGroupBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication auth) {
        return groupsService.getGroupBySlug(slug)
                .flatMap(group -> feedForGroup(group.getGroupKey(), nextPage, size, javaProxied, auth));
    }

    @GetMapping({ "/group/{groupKey}" })
    @PreAuthorize("permitAll")
    public Mono<FeedDto> feedForGroup(
            @PathVariable String groupKey,
            @RequestParam(required = false) Optional<String> nextPage,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication auth) {
        log.info("Incoming request: /feed/group/{}", groupKey);

        val np = nextPage.map(nextPageMapper::b64StringToNextPage);
        val checkinsFrom = np.map(NextPage::getChekinsTimestamp);

        return getNetworkEventIdByParentHash(groupKey, "posts", javaProxied)
                .flatMap(eventId -> Mono.<FeedDto>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(postService.groupSlidersSkewed(
                        postService.getPostsForGroup(np.map(NextPage::getTimestamp), groupKey, maybeAuthorized(auth),
                                NON_CHECKIN, false),
                        postService.getPostsForGroup(checkinsFrom, groupKey, maybeAuthorized(auth), CHECK_IN, true),
                        false, size, checkinsFrom.isEmpty(), np.map(NextPage::getSliderShift))
                        .name("feed").tag("op", "group")
                        .metrics()
                        .map(postMapper::postComponentToPostComponentDto)
                        .collectList()
                        .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                        .map(postComponentDtos -> toFeedDto(np, postComponentDtos))
                        .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException())));
    }

    private Mono<String> getNetworkEventIdByParentHash(String key, String collectionName, boolean javaProxied) {
        if (javaProxied || key == null) {
            return Mono.empty();
        }
        String shorthash = hashShort(key);
        log.info("Hash produced: {}", shorthash);

        Query query = Query.query(Criteria.where("collectionName").is(collectionName).and("status")
                .is("pending").and("parentHash").is(shorthash))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        log.info("Query: {}", query);

        return mongoTemplate.find(query, org.bson.Document.class, "network_events")
                .next()
                .map(doc -> {
                    String id = doc.getObjectId("_id").toHexString();
                    log.info("Found: id={}", id);
                    return id;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Not found for hash: {}", shorthash);
                    return Mono.empty();
                }));
    }

    private Mono<String> getNetworkEventIdByAuthorHash(String key, String collectionName, boolean javaProxied) {
        if (javaProxied || key == null) {
            return Mono.empty();
        }
        String shorthash = hashShort(key);

        Query query = Query.query(Criteria.where("collectionName").is(collectionName).and("status")
                .is("pending").and("authorHash").is(shorthash))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        return mongoTemplate.find(query, org.bson.Document.class, "network_events")
                .next()
                .map(doc -> doc.getObjectId("_id").toHexString())
                .switchIfEmpty(Mono.empty());
    }

    private Mono<String> getNetworkEventIdByParentHash2(String key, String collectionName, boolean javaProxied) {
        if (javaProxied || key == null) {
            return Mono.empty();
        }
        String shorthash = hashShort(key);

        Query query = Query.query(Criteria.where("collectionName").is(collectionName).and("status")
                .is("pending").and("parentHash2").is(shorthash))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        return mongoTemplate.find(query, org.bson.Document.class, "network_events")
                .next()
                .map(doc -> doc.getObjectId("_id").toHexString())
                .switchIfEmpty(Mono.empty());
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
                    String docGeohash = doc.getString("geoHash");
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
