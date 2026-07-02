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
import to.orbis.v2.backend.mappers.NotificationMapper;
import to.orbis.v2.backend.models.dto.NotificationDto;
import to.orbis.v2.backend.models.dto.UnreadNotificationsDto;
import to.orbis.v2.backend.services.NotificationsService;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    NotificationMapper notificationMapper;
    NotificationsService notificationsService;


    @GetMapping
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "getNotifications", security = @SecurityRequirement(name = "firebase"))
    public Mono<List<NotificationDto>> getNotifications(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication
    ) {
        return notificationsService.getNotifications(authentication.getName(), PageRequest.of(page, size))
                .map(notificationMapper::extendedNotificationToNotificationDto)
                .collectList()
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list))
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @DeleteMapping("/{notificationKey}")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "deleteNotification", security = @SecurityRequirement(name = "firebase"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteNotification(@PathVariable String notificationKey,
                                         Authentication authentication) {
        return notificationsService.deleteNotification(notificationKey, authentication.getName());
    }

    @GetMapping("/unreadcount")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "getUnreadNotificationsCount", security = @SecurityRequirement(name = "firebase"))
    public Mono<UnreadNotificationsDto> getUnreadNotificationsCount(Authentication authentication) {
        return notificationsService.getUnreadNotificationsCount(authentication.getName())
                .map(t -> new UnreadNotificationsDto(t.getT1(), t.getT2()))
                .switchIfEmpty(Mono.just(new UnreadNotificationsDto(0L, 0L)));
    }

    @PostMapping("/seen")
    @PreAuthorize("isAuthenticated")
    @Operation(operationId = "setSeenStatus", security = @SecurityRequirement(name = "firebase"))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> setSeenStatus(@RequestBody List<String> notificationKeys, Authentication authentication) {
        return notificationsService.setSeenStatus(notificationKeys, authentication.getName());
    }

}
