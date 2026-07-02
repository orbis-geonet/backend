package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.UserSubscriptionDto;
import to.orbis.v2.backend.models.dto.stripe.StripeSecretDto;
import to.orbis.v2.backend.services.SubscriptionsService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class UsersSubscriptionController {
    SubscriptionsService subscriptionsService;

    @PreAuthorize("isAuthenticated")
    @PostMapping("/subscription/{subscriptionKey}/subscribe")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "400", description = "There is no product in Stripe for this subscription."),
            @ApiResponse(responseCode = "400", description = "There is no prise in Stripe for this subscription."),
            @ApiResponse(responseCode = "400", description = "You cannot use this subscription, because group main admin didn't activate it."),
            @ApiResponse(responseCode = "400", description = "You cannot subscribe, user has already activated this subscription."),
            @ApiResponse(responseCode = "404", description = "User not found."),
            @ApiResponse(responseCode = "404", description = "Group not found"),
            @ApiResponse(responseCode = "400", description = "Stripe error. Cannot create customer."),
            @ApiResponse(responseCode = "400", description = "Stripe error. Cannot create payment intent.")
    })
    @Operation(description = "Subscribe", operationId = "subscribe", security = @SecurityRequirement(name = "firebase"))
    public Mono<StripeSecretDto> subscribe(
            @PathVariable String subscriptionKey,
            Authentication authentication
    ) {
        log.info("subscribe: subscriptionKey: {}, userKey: {}", subscriptionKey, authentication.getName());
        return subscriptionsService.subscribe(subscriptionKey, authentication.getName());
    }

    @PreAuthorize("isAuthenticated")
    @PostMapping("/subscription/{subscriptionKey}/unsubscribe")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "400", description = "You cannot unsubscribe, user doesn't subscribe."),
            @ApiResponse(responseCode = "400", description = ""),
            @ApiResponse(responseCode = "400", description = ""),
            @ApiResponse(responseCode = "400", description = ""),
            @ApiResponse(responseCode = "400", description = ""),
    })
    @Operation(description = "Unsubscribe",  operationId = "unsubscribe", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> unsubscribe(
            @PathVariable String subscriptionKey,
            Authentication authentication
    ) {
        log.info("unsubscribe: subscriptionKey: {}, userKey: {}", subscriptionKey, authentication.getName());
        return subscriptionsService.unsubscribe(subscriptionKey, authentication.getName());
    }

    @PreAuthorize("isAuthenticated")
    @GetMapping("/subscriptions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
    })
    @Operation(description = "See subscriptions of the user", operationId = "getMySubscriptions", security = @SecurityRequirement(name = "firebase"))
    public Mono<List<UserSubscriptionDto>> getMySubscriptions(
            @RequestParam(required = false) String groupKey,
            Authentication authentication
    ) {
        log.info("subscriptions: userKey: {}", authentication.getName());
        return subscriptionsService.getMySubscriptions(groupKey, authentication.getName())
                .buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }
}
