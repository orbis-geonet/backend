package to.orbis.v2.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.FirebaseConfigurationOptions;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.repositories.*;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ShortLinksService {

    WebClient webClient;
    FirebaseConfigurationOptions options;
    ObjectMapper objectMapper;

    PostsRepository postsRepository;
    GroupsRepository groupsRepository;
    UsersRepository usersRepository;
    PlacesRepository placesRepository;
    String baseUrl;
    String domainUriPrefix;
    String cdnBaseUrl;
    String androidPacakgeName;
    String bundleId;
    String appStoreId;

    public ShortLinksService(WebClient.Builder webClientBuilder, FirebaseConfigurationOptions options,
                             ObjectMapper objectMapper, PostsRepository postsRepository, GroupsRepository groupsRepository,
                             UsersRepository usersRepository, PlacesRepository placesRepository,
                             @Value("${orbis.baseUrl}") String baseUrl,
                             @Value("${orbis.shortLinkPrefix}") String domainUriPrefix,
                             @Value("${orbis.cdnBaseUrl}") String cdnBaseUrl,
                             @Value("${orbis.android.packageName}") String androidPackageName,
                             @Value("${orbis.ios.bundleId}") String bundleId,
                             @Value("${orbis.ios.appStoreId}") String appStoreId) {
        this.webClient = webClientBuilder
                .baseUrl("https://firebasedynamiclinks.googleapis.com")
                .build();
        this.options = options;
        this.objectMapper = objectMapper;
        this.postsRepository = postsRepository;
        this.groupsRepository = groupsRepository;
        this.usersRepository = usersRepository;
        this.placesRepository = placesRepository;
        this.baseUrl = baseUrl;
        this.domainUriPrefix = domainUriPrefix;
        this.cdnBaseUrl = cdnBaseUrl;
        this.androidPacakgeName = androidPackageName;
        this.bundleId = bundleId;
        this.appStoreId = appStoreId;
    }

    private static final String POST_TEMPLATE = "https://orbis.social/post/%s";
    private static final String GROUP_TEMPLATE = "https://orbis.social/g/%s";
    private static final String PLACE_TEMPLATE = "https://orbis.social/p/%s";
    private static final String USER_TEMPLATE = "https://orbis.social/u/%s";

    public Mono<ShareLink> generateShortGroupLink(String shareLinkName, String name, String description) {
        var fullLink = String.format(GROUP_TEMPLATE, shareLinkName);
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/v1/shortLinks")
                        .queryParam("key", options.getApiKey()).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new LinkRequest(domainUriPrefix, fullLink, name, description,
                        baseUrl + "previews/groups/" + shareLinkName, androidPacakgeName, bundleId, appStoreId)))
                .retrieve()
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse
                                .bodyToMono(String.class)
                                .flatMap(e -> Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e))))
                .bodyToMono(mapRef)
                .flatMap(map -> map.containsKey("shortLink")
                        ? Mono.just(new ShareLink(map.get("shortLink").toString(), fullLink))
                        : Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, json(map))));
    }

    public Mono<ShareLink> generateShortUserLink(String shareLinkName, String name) {
        var fullLink = String.format(USER_TEMPLATE, shareLinkName);
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/v1/shortLinks")
                        .queryParam("key", options.getApiKey()).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new LinkRequest(domainUriPrefix, fullLink, name, null,
                        baseUrl + "previews/users/" + shareLinkName, androidPacakgeName, bundleId, appStoreId)))
                .retrieve()
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse
                                .bodyToMono(String.class)
                                .flatMap(e -> Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e))))
                .bodyToMono(mapRef)
                .flatMap(map -> map.containsKey("shortLink")
                        ? Mono.just(new ShareLink(map.get("shortLink").toString(), fullLink))
                        : Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, json(map))));
    }

    public Mono<ShareLink> generateShortPlaceLink(String shareLinkName, String name, String description) {
        var fullLink = String.format(PLACE_TEMPLATE, shareLinkName);
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/v1/shortLinks")
                        .queryParam("key", options.getApiKey()).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new LinkRequest(domainUriPrefix, fullLink, name, description,
                        baseUrl + "previews/places/" + shareLinkName, androidPacakgeName, bundleId, appStoreId)))
                .retrieve()
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse
                                .bodyToMono(String.class)
                                .flatMap(e -> Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e))))
                .bodyToMono(mapRef)
                .flatMap(map -> map.containsKey("shortLink")
                        ? Mono.just(new ShareLink(map.get("shortLink").toString(), fullLink))
                        : Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, json(map))));
    }

    @Data
    public static class LinkRequest {
        public LinkRequest(String domainUriPrefix, String link, String title, String details, String imageLink,
                           String packageName, String bundleId, String appStoreId) {
            this.dynamicLinkInfo = new DynamicLinkInfo(domainUriPrefix, link, title, details, imageLink, packageName, bundleId, appStoreId);
        }

        DynamicLinkInfo dynamicLinkInfo;
    }

    @Data
    private static class DynamicLinkInfo {
        String domainUriPrefix;
        String link;
        AndroidInfo androidInfo;
        IosInfo iosInfo;
        SocialMetaTagInfo socialMetaTagInfo;

        public DynamicLinkInfo(String domainUriPrefix, String link, String title, String description, String imageUrl,
                               String androidPackageName, String bundleId, String appStoreId) {
            this.domainUriPrefix = domainUriPrefix;
            this.link = link;
            socialMetaTagInfo = new SocialMetaTagInfo(title, description, imageUrl);
            this.androidInfo = new AndroidInfo(androidPackageName);
            this.iosInfo = new IosInfo(bundleId, appStoreId);
        }
    }

    @Data
    private static class AndroidInfo {
        String androidPackageName;
    }

    @Data
    private static class IosInfo {
        String iosBundleId;
        String iosAppStoreId;
    }

    ParameterizedTypeReference<Map<String, Object>> mapRef = new ParameterizedTypeReference<>() {
    };

    public Mono<ShareLink> generateShortPostLink(Post post) {
        var fullLink = String.format(POST_TEMPLATE, post.getPostKey());
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/v1/shortLinks")
                        .queryParam("key", options.getApiKey()).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new LinkRequest(domainUriPrefix, fullLink, post.getTitle(), post.getDetails(),
                        baseUrl + "previews/" + post.getPostKey(), androidPacakgeName, bundleId, appStoreId)))
                .retrieve()
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse
                                .bodyToMono(String.class)
                                .flatMap(e -> Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e))))
                .bodyToMono(mapRef)
                .flatMap(map -> map.containsKey("shortLink")
                        ? Mono.just(new ShareLink(map.get("shortLink").toString(), fullLink))
                        : Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, json(map))));
    }

    public Mono<String> buildImageUrl(String postKey) {
        return postsRepository.findOneByPostKey(postKey)
                .flatMap(p -> Mono.justOrEmpty(buildImageUrl(p)))
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post not found or image is not present")));
    }

    public Mono<String> buildGroupImageUrl(String groupKey) {
        return groupsRepository.findOneByGroupKeyAndDeletedFalse(groupKey)
                .flatMap(g -> Mono.justOrEmpty(buildImageUrl(g)))
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Group not found or image is not present")));
    }

    public Mono<String> buildUserImageUrl(String userKey) {
        return usersRepository.findOneByUserKey(userKey)
                .flatMap(g -> Mono.justOrEmpty(buildImageUrl(g)))
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("User not found or image is not present")));
    }

    public Mono<String> buildPlaceImageUrl(String placeKey) {
        return placesRepository.findOneByPlaceKey(placeKey)
                .flatMap(g -> Mono.justOrEmpty(buildImageUrl(g)))
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("User not found or image is not present")));
    }

    private String buildImageUrl(Place place) {
        if (place.getImageName() == null || place.getImageName().isEmpty()) {
            return null;
        }

        val fileName = place.getImageName();
        return urlFromName(fileName, "placePictures/");
    }


    private String buildImageUrl(User u) {
        if (u.getImageName() == null || u.getImageName().isBlank()) {
            return u.getProviderImageUrl();
        }

        val fileName = u.getImageName();
        return urlFromName(fileName, "profilePictures/");
    }

    private String buildImageUrl(Group g) {
        if (g.getImageName() == null || g.getImageName().isEmpty()) {
            return null;
        }

        val fileName = g.getImageName();
        return urlFromName(fileName, "groupPictures/");
    }

    private String urlFromName(String fileName, String s) {
        int endIndex = fileName.lastIndexOf('.');
        if (endIndex == -1) {
            return null;
        }
        val justName = fileName.substring(0, endIndex);
        val ext = fileName.substring(endIndex);

        return signUrl(s + justName + "_200x200" + ext).toString();
    }

    private String buildImageUrl(Post post) {
        if (post.getMediaUrls() == null || post.getMediaUrls().isEmpty()) {
            return null;
        }

        val fileName = post.getMediaUrls().get(0);
        String url = getUrl(fileName, post.getType(), true);
        if (url == null) return null;

        return signUrl(url).toString();
    }

    private String getUrl(String fileName, PostType type, boolean preview) {
        int endIndex = fileName.lastIndexOf('.');
        if (endIndex == -1) {
            return null;
        }
        val justName = fileName.substring(0, endIndex);
        val ext = fileName.substring(endIndex);

        var url = "";

        switch (type) {
            case IMAGE:
                url = "posts/images/" + justName + "_200x200" + ext;
                break;
            case EVENT:
                url = "events/images/" + justName + "_200x200" + ext;
                break;
            case VIDEO:
                if (preview) {
                    url = "posts/videos/" + justName + "_200x200.jpg";
                } else {
                    url = "posts/videos/" + fileName;
                }
                break;
            default:
                return null;
        }
        return url;
    }

    public URL signUrl(String url) {
        val storage = StorageOptions.newBuilder().build().getService();
        val blobInfo = BlobInfo.newBuilder(BlobId.of("orbis-v2.appspot.com", url)).build();

        return storage.signUrl(blobInfo, 600, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());
    }

    @SneakyThrows
    private String json(Map<String, Object> map) {
        return objectMapper.writeValueAsString(map);
    }

    @Data
    @AllArgsConstructor
    private static class SocialMetaTagInfo {
        String socialTitle;
        String socialDescription;
        String socialImageLink;
    }

    public List<String> signAllUrls(ExtendedPost post) {
        if (post == null || post.getMediaUrls() == null) return null;

        return post.getMediaUrls().stream().flatMap(url -> signMedia(url, post.getType()).stream()).collect(Collectors.toList());
    }

    private Optional<String> signMedia(String url, PostType type) {
        return Optional.ofNullable(getUrl(url, type, false))
                //.map(s -> signUrl(s).toString())
                .map(this::cdnUrl)
                ;
    }

    private String cdnUrl(String url) {
        return String.format(cdnBaseUrl + "%s", url);
    }
}

