package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.mappers.SubscriptionMapper;
import to.orbis.v2.backend.models.StatisticSubscriptionType;
import to.orbis.v2.backend.models.dto.*;
import to.orbis.v2.backend.models.dto.stripe.CommissionDto;
import to.orbis.v2.backend.services.GroupsService;
import to.orbis.v2.backend.services.PurchaseService;
import to.orbis.v2.backend.services.SubscriptionsService;

import java.util.ArrayList;
import java.util.List;


@Slf4j
@Validated
@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupsSubscriptionController {
    SubscriptionsService subscriptionsService;
    PurchaseService purchaseService;
    SubscriptionMapper subscriptionMapper;
    GroupsService groupsService;

    @PreAuthorize("permitAll")
    @GetMapping("/subscription/slug/{slug}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error")
    })
    @Operation(description = "Get all subscriptions of the group", operationId = "getAllGroupSubscription")
    public Mono<List<SubscriptionDto>> getAllGroupSubscriptionBySlug(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication
    ) {
        log.info("getAllGroupSubscriptionBySlug: groupKey: {}", slug);
        return groupsService.getGroupBySlug(slug)
                .flatMap(group -> getAllGroupSubscription(group.getGroupKey(), page, size, authentication));
    }

    @PreAuthorize("permitAll")
    @GetMapping("/subscription/{groupKey}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error")
    })
    @Operation(description = "Get all subscriptions of the group", operationId = "getAllGroupSubscription")
    public Mono<List<SubscriptionDto>> getAllGroupSubscription(
            @PathVariable String groupKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication
    ) {
        log.info("getAllGroupSubscription: groupKey: {}", groupKey);
        val userKey = (authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName()
                : null;
        return subscriptionsService.getAllGroupSubscription(groupKey, userKey, PageRequest.of(page, size))
                .buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @PreAuthorize("permitAll")
    @GetMapping("/subscription/one/{subscriptionKey}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Subscription not found.")
    })
    @Operation(description = "Get one subscription by Key", operationId = "getSubscription")
    public Mono<SubscriptionDto> getSubscription(
            @PathVariable String subscriptionKey
    ) {
        log.info("getSubscription: subscriptionKey: {}", subscriptionKey);
        return subscriptionsService.getSubscription(subscriptionKey)
                .map(subscriptionMapper::subscriptionToSubscriptionDto);
    }

    @PreAuthorize("isAuthenticated")
    @PostMapping("/subscription/{groupKey}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Subscription not found."),
            @ApiResponse(responseCode = "400", description = "You should be main admin to create subscription."),
            @ApiResponse(responseCode = "400", description = "Stripe error. Cannot create product."),
            @ApiResponse(responseCode = "400", description = "Stripe error. Cannot create a new price.")
    })
    @Operation(description = "Create a new subscription for a group", operationId = "createSubscription", security = @SecurityRequirement(name = "firebase"))
    public Mono<SubscriptionDto> createSubscription(
            @PathVariable String groupKey,
            @RequestBody @Validated SubscriptionCreateDto subscription,
            Authentication authentication
    ) {
        log.info("createSubscription: groupKey: {}, subscription: {}, userName: {}", groupKey, subscription, authentication.getName());
        return subscriptionsService
                .createSubscription(
                        subscriptionMapper.subscriptionCreateDtoToSubscription(subscription, groupKey, authentication.getName())
                ).map(subscriptionMapper::subscriptionToSubscriptionDto);
    }

    @PreAuthorize("isAuthenticated")
    @PutMapping("/subscription/{groupKey}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Subscription not found."),
            @ApiResponse(responseCode = "400", description = "You should be main admin to edit subscription."),
            @ApiResponse(responseCode = "400", description = "Stripe error. Cannot archive price."),
            @ApiResponse(responseCode = "400", description = "Stripe error. Cannot create a new price.")
    })
    @Operation(description = "Edit a subscription be the Key", operationId = "updateSubscription", security = @SecurityRequirement(name = "firebase"))
    public Mono<SubscriptionDto> updateSubscription(
            @PathVariable String groupKey,
            @RequestBody @Validated SubscriptionUpdateDto subscription,
            Authentication authentication
    ) {
        log.info("updateSubscription: groupKey: {}, subscription: {}, userName: {}", groupKey, subscription, authentication.getName());
        return subscriptionsService
                .updateSubscription(
                        subscriptionMapper.subscriptionUpdateDtoToSubscription(subscription, groupKey, authentication.getName())
                ).map(subscriptionMapper::subscriptionToSubscriptionDto);
    }

    @PreAuthorize("isAuthenticated")
    @DeleteMapping("/subscription/{groupKey}/{subscriptionKey}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Subscription not found."),
            @ApiResponse(responseCode = "400", description = "You should be main admin to delete subscription."),
            @ApiResponse(responseCode = "400", description = "Stripe error. Cannot archive price.")
    })
    @Operation(description = "Delete subscription be the Key", operationId = "deleteSubscription", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> deleteSubscription(
            @PathVariable String groupKey,
            @PathVariable String subscriptionKey,
            Authentication authentication
    ) {
        log.info("deleteSubscription: groupKey: {}, subscriptionKey: {}, userName: {}", groupKey, subscriptionKey, authentication.getName());
        return subscriptionsService.deleteSubscription(groupKey, subscriptionKey, authentication.getName());
    }

    @PreAuthorize("isAuthenticated")
    @PostMapping("/subscription/{groupKey}/activate")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Subscription not found."),
            @ApiResponse(responseCode = "400", description = "You should be main admin to activate subscription."),
            @ApiResponse(responseCode = "400", description = "There are no subscription for group."),
            @ApiResponse(responseCode = "400", description = "There is no main user for group."),
            @ApiResponse(responseCode = "400", description = "There is no validated stripe account for main user. userKey: {}")
    })
    @Operation(description = "Activate all subscription for a group", operationId = "subscriptionGroupActivate", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> subscriptionGroupActivate(
            @PathVariable String groupKey,
            Authentication authentication
    ) {
        log.info("subscriptionActivate: groupKey: {}, userKey: {}", groupKey, authentication.getName());
        return subscriptionsService.subscriptionGroupActivate(groupKey, authentication.getName());
    }

    @PreAuthorize("isAuthenticated")
    @PostMapping("/subscription/{groupKey}/deactivate")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Subscription not found."),
            @ApiResponse(responseCode = "400", description = "You should be main admin to deactivate subscription."),
            @ApiResponse(responseCode = "400", description = "Subscription doesn't activated."),
            @ApiResponse(responseCode = "400", description = "There are {N} users, who were subscribed. Would you like to unsubscribe all users?")
    })
    @Operation(description = "Deactivate all subscription for a group", operationId = "subscriptionGroupDeactivate", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> subscriptionGroupDeactivate(
            @PathVariable String groupKey,
            @RequestParam boolean sure,
            Authentication authentication
    ) {
        log.info("subscriptionGroupDeactivate: groupKey: {}, userKey: {}, sure {}", groupKey, authentication.getName(), sure);
        return subscriptionsService.subscriptionGroupDeactivate(groupKey, authentication.getName(), sure);
    }

    @PreAuthorize("isAuthenticated")
    @GetMapping("/subscription/info")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error")
    })
    @Operation(description = "Get commission info for a group", operationId = "subscriptionInfo", security = @SecurityRequirement(name = "firebase"))
    public Mono<CommissionDto> subscriptionInfo() {
        return subscriptionsService.getSubscriptionInfo();
    }

    @PreAuthorize("isAuthenticated")
    @GetMapping("/subscription/{groupKey}/subscribers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Subscription not found."),
            @ApiResponse(responseCode = "400", description = "You should be main admin to delete subscription."),
    })
    @Operation(description = "Get all subscribers", operationId = "deleteSubscription", security = @SecurityRequirement(name = "firebase"))
    public Mono<List<SimplifiedUserDto>> getSubscribers(
            @PathVariable String groupKey,
            @RequestParam(required = false) String subscriptionKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication
    ) {
        log.info("getSubscribers: groupKey: {}, subscriptionKey: {}, userName: {}", groupKey, subscriptionKey, authentication.getName());
        return subscriptionsService.getSubscribers(groupKey, subscriptionKey, authentication.getName(), PageRequest.of(page, size))
                .buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @PreAuthorize("isAuthenticated")
    @GetMapping("/subscription/{groupKey}/{subscriptionKey}/statistic")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Subscription not found."),
            @ApiResponse(responseCode = "400", description = "You should be main admin to delete subscription."),
    })
    @Operation(description = "Get statistic", operationId = "deleteSubscription", security = @SecurityRequirement(name = "firebase"))
    public Mono<StatisticFullSubscriptionDto> getStatisticInfo(
            @PathVariable String groupKey,
            @PathVariable String subscriptionKey,
            @RequestParam StatisticSubscriptionType type,
            Authentication authentication
    ) {
        log.info("getSubscribers: groupKey: {}, subscriptionKey: {}, userName: {}", groupKey, subscriptionKey, authentication.getName());
        return subscriptionsService.getStatistic(groupKey, subscriptionKey, authentication.getName(), type);
    }

    @PreAuthorize("isAuthenticated")
    @GetMapping("/purchases/{groupKey}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
    })
    @Operation(description = "See purchases of the user", operationId = "getMyPurchases", security = @SecurityRequirement(name = "firebase"))
    public Mono<List<UserPurchaseGroupDto>> getAllGroupPurchase(
            @PathVariable String groupKey,
            @RequestParam(required = false) String purchaseKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication
    ) {
        log.info("getAllGroupPurchase: groupKey:{} purchaseKey:{} userKey: {}", groupKey, purchaseKey, authentication.getName());
        return purchaseService.getAllGroupPurchase(groupKey, purchaseKey, authentication.getName(), PageRequest.of(page, size))
                .map(subscriptionMapper::toUserPurchaseGroupDto)
                .buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @PreAuthorize("isAuthenticated")
    @GetMapping("/purchases/{groupKey}/{purchaseKey}/statistic")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Subscription not found."),
            @ApiResponse(responseCode = "400", description = "You should be main admin to delete subscription."),
    })
    @Operation(description = "Get statistic", operationId = "deleteSubscription", security = @SecurityRequirement(name = "firebase"))
    public Mono<StatisticFullSubscriptionDto> getPurchaseStatisticInfo(
            @PathVariable String groupKey,
            @PathVariable String purchaseKey,
            @RequestParam StatisticSubscriptionType type,
            Authentication authentication
    ) {
        log.info("getPurchaseStatisticInfo: groupKey: {}, purchaseKey: {}, userName: {}", groupKey, purchaseKey, authentication.getName());
        return purchaseService.getStatistic(groupKey, purchaseKey, authentication.getName(), type);
    }
}
