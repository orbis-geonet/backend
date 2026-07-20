package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.mappers.PostMapper;
import to.orbis.v2.backend.models.Language;
import to.orbis.v2.backend.models.PostType;
import to.orbis.v2.backend.models.dto.PostDto;
import to.orbis.v2.backend.models.requests.posts.CreatePostRequest;
import to.orbis.v2.backend.models.requests.posts.UpdatePostRequest;
import to.orbis.v2.backend.services.PostService;
import to.orbis.v2.backend.utils.ControllerUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import to.orbis.v2.backend.exceptions.ForwardToNodeJsException;

import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import static to.orbis.v2.backend.utils.ControllerUtils.maybeAuthorized;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostsController {

    PostMapper postMapper;
    PostService postService;
    ReactiveMongoTemplate mongoTemplate;
    to.orbis.v2.backend.services.NetworkEventLookupService networkEventLookupService;

    @GetMapping("/{postKey}")
    @PreAuthorize("permitAll")
    public Mono<PostDto> getPost(
            @PathVariable String postKey,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied,
            Authentication auth) {
        log.info("Incoming request: /posts/{}", postKey);
        return getNetworkEventIdByKey(postKey, "posts", javaProxied)
                .flatMap(eventId -> Mono.<PostDto>error(new ForwardToNodeJsException(eventId)))
                .switchIfEmpty(postService.getPost(postKey, maybeAuthorized(auth))
                        .map(postMapper::extendedPostToPostDto)
                        .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post with this key is not found"))));
    }

    @PutMapping("/{postKey}")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "updatePost", security = @SecurityRequirement(name = "firebase"))
    public Mono<PostDto> updatePost(@PathVariable String postKey, @RequestBody UpdatePostRequest updatePost,
            Authentication auth) {
        return postService.updatePost(postKey, postMapper.updateRequestToPost(updatePost), auth.getName())
                .map(postMapper::extendedPostToPostDto)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post with this key is not found")));
    }

    @PutMapping("/{postKey}/share")
    @PreAuthorize("permitAll")
    public Mono<String> sharePost(@PathVariable String postKey) {
        return postService.sharePost(postKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post with this key is not found")));
    }

    @PostMapping("/{postKey}/report")
    public Mono<String> reportPost(@PathVariable String postKey, @RequestBody(required = false) String reason,
            Authentication authentication) {

        return postService
                .reportPost(postKey, Optional.ofNullable(reason).orElse("not specified"),
                        maybeAuthorized(authentication))
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post with this key is not found")));
    }

    @DeleteMapping("/{postKey}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deletePost(@PathVariable String postKey, Authentication auth) {
        return postService.deletePost(postKey, auth.getName());
    }

    @PostMapping()
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "createPost", security = @SecurityRequirement(name = "firebase"))
    public Mono<PostDto> createPost(
            @RequestBody @Validated CreatePostRequest createPostRequest,
            Authentication authentication) {
        return postService.createPost(
                postMapper.createRequestToPost(createPostRequest),
                authentication.getName(),
                createPostRequest.isCheckin()).map(postMapper::extendedPostToPostDto);
    }

    @PutMapping("/{postKey}/like")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "likePost", security = @SecurityRequirement(name = "firebase"))
    public Mono<PostDto> likePost(@PathVariable String postKey, Authentication auth) {
        return postService.likePost(postKey, auth.getName())
                .map(postMapper::extendedPostToPostDto)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post not found")));
    }

    @DeleteMapping("/{postKey}/like")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "unlikePost", security = @SecurityRequirement(name = "firebase"))
    public Mono<PostDto> unlikePost(@PathVariable String postKey, Authentication auth) {
        return postService.unlikePost(postKey, auth.getName())
                .map(postMapper::extendedPostToPostDto)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Post not found")));
    }

    private String hashKeyFull(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Error hashing key", e);
            return "";
        }
    }

    private Mono<String> getNetworkEventIdByKey(String key, String collectionName, boolean javaProxied) {
        return networkEventLookupService.byKey(collectionName, key, javaProxied);
    }
}
