package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;
import to.orbis.v2.backend.services.ShortLinksService;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/previews")
public class PreviewsController {

    ShortLinksService shortLinksService;

    @GetMapping("/{postKey}")
    public Mono<ResponseEntity<Void>> preview(@PathVariable String postKey) {
        return shortLinksService.buildImageUrl(postKey)
                .map(u -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(u)).build());
    }

    @GetMapping("/groups/{groupKey}")
    public Mono<ResponseEntity<Void>> previewGroup(@PathVariable String groupKey) {
        return shortLinksService.buildGroupImageUrl(groupKey)
                .map(u -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(u)).build());
    }

    @GetMapping("/users/{userKey}")
    public Mono<ResponseEntity<Void>> previewUser(@PathVariable String userKey) {
        return shortLinksService.buildUserImageUrl(userKey)
                .map(u -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(u)).build());
    }

    @GetMapping("/users/{placeKey}")
    public Mono<ResponseEntity<Void>> previewPlace(@PathVariable String placeKey) {
        return shortLinksService.buildPlaceImageUrl(placeKey)
                .map(u -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(u)).build());
    }

    @PostMapping("/sign")
    public Mono<ResponseEntity<Void>> previewUrl(@RequestBody String path) {
        return Mono.just(shortLinksService.signUrl(path))
                .map(u -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(u.toString())).build());
    }
}
