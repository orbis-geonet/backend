package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.UserPurchaseDto;
import to.orbis.v2.backend.models.dto.UserSubscriptionDto;
import to.orbis.v2.backend.models.dto.email.EmailType;
import to.orbis.v2.backend.models.dto.crypto.CryptoPaymentDto;
import to.orbis.v2.backend.services.PurchaseService;
import to.orbis.v2.backend.services.SubscriptionsService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class UsersPurchaseController {
    PurchaseService purchaseService;

    @PreAuthorize("isAuthenticated")
    @PostMapping("/purchase/{purchaseKey}/buy")
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
    @Operation(description = "Buy purchase", operationId = "buyPurchase", security = @SecurityRequirement(name = "firebase"))
    public Mono<CryptoPaymentDto> buyPurchase(
            @PathVariable String purchaseKey,
            @RequestParam(defaultValue = "1") Integer number,
            @RequestParam String payerWallet,
            Authentication authentication
    ) {
        log.info("buy: purchaseKey: {}, userKey: {}, payerWallet: {}", purchaseKey, authentication.getName(), payerWallet);
        return purchaseService.buyPurchase(purchaseKey, number, authentication.getName(), payerWallet);
    }

    @PreAuthorize("isAuthenticated")
    @GetMapping("/purchases")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
    })
    @Operation(description = "See purchases of the user", operationId = "getMyPurchases", security = @SecurityRequirement(name = "firebase"))
    public Mono<List<UserPurchaseDto>> getMyPurchases(
            @RequestParam(required = false) String groupKey,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            Authentication authentication
    ) {
        log.info("getMyPurchases: userKey: {}", authentication.getName());
        return purchaseService.getMyPurchases(groupKey, authentication.getName(), PageRequest.of(page, size))
                .buffer()
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new to.orbis.v2.backend.exceptions.ForwardToNodeJsException()));
    }

    @PreAuthorize("isAuthenticated")
    @GetMapping("/purchases/{userPurchaseKey}/resend-email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
    })
    @Operation(description = "Email was send", operationId = "resendPurchaseEmail", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> resendPurchaseEmail(
            @PathVariable String userPurchaseKey,
            Authentication authentication
    ) {
        log.info("resendPurchaseEmail: userPurchaseKey: {} userKey: {}", userPurchaseKey, authentication.getName());
        return purchaseService.resendPurchaseEmail(userPurchaseKey, authentication.getName());
    }
}
