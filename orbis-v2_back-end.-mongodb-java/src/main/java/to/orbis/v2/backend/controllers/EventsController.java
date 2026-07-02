package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.mappers.GroupMapper;
import to.orbis.v2.backend.mappers.PostMapper;
import to.orbis.v2.backend.models.dto.PostDto;
import to.orbis.v2.backend.models.dto.SimplifiedUserDto;
import to.orbis.v2.backend.services.EventsService;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventsController {

    EventsService eventsService;
    GroupMapper groupMapper;
    PostMapper postMapper;

    @GetMapping("/{postKey}/attendees")
    Mono<List<SimplifiedUserDto>> getAttendees(@PathVariable String postKey,
                                               @RequestParam(required = false, defaultValue = "0") int page,
                                               @RequestParam(required = false, defaultValue = "20") int size) {
        return eventsService.getAttendees(postKey, PageRequest.of(page, size))
                .map(groupMapper::simplifiedUserToSimplifiedUserDto)
                .collectList()
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @GetMapping("/attending")
    @PreAuthorize("isAuthenticated")
    Mono<List<PostDto>> getAttending(@RequestParam(required = false, defaultValue = "false") boolean pastEvents,
                                     @RequestParam(required = false, defaultValue = "0") int page,
                                     @RequestParam(required = false, defaultValue = "20") int size,
                                     Authentication authentication) {
        return eventsService.getAttending(pastEvents, authentication.getName(), PageRequest.of(page, size))
                .map(postMapper::extendedPostToPostDto)
                .collectList()
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @PutMapping("/{postKey}/attend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "attendEvent", security = @SecurityRequirement(name = "firebase"))
    Mono<Void> attendEvent(@PathVariable String postKey, Authentication authentication) {
        return eventsService.attend(postKey, authentication.getName());
    }

    @DeleteMapping("/{postKey}/attend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "notAttendEvent", security = @SecurityRequirement(name = "firebase"))
    Mono<Void> notAttendEvent(@PathVariable String postKey, Authentication authentication) {
        return eventsService.notAttend(postKey, authentication.getName());
    }
}
