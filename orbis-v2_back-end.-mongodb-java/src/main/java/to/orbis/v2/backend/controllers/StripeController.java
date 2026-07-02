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
import to.orbis.v2.backend.mappers.StripeMapper;
import to.orbis.v2.backend.models.dto.stripe.CreateAccountDto;
import to.orbis.v2.backend.models.dto.stripe.CreateAccountResponseDto;
import to.orbis.v2.backend.models.dto.stripe.StripeAccountInfoDto;
import to.orbis.v2.backend.services.StripeAccountService;

@Slf4j
@Validated
@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
public class StripeController {
    private final StripeAccountService stripeAccountService;
    private final StripeMapper stripeMapper;

    @PostMapping
    @PreAuthorize("isAuthenticated")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "400", description = "User has already had an activated stripe account. You cannot create a new one."),
    })
    @Operation(description = "Create stripe account for an user", operationId = "createAccount", security = @SecurityRequirement(name = "firebase"))
    public Mono<CreateAccountResponseDto> createAccount(
            @RequestBody CreateAccountDto createAccountDto,
            Authentication authentication
    ) {
        log.info("createAccount: createAccountDto: {}, userKey: {}", createAccountDto, authentication.getName());
        return stripeAccountService.createAccount(
                stripeMapper.createAccountDtoToStripeAccount(createAccountDto, authentication.getName())
        );
    }

    @PutMapping
    @PreAuthorize("isAuthenticated")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "400", description = "Activated/Validated stripe account not found")
    })
    @Operation(description = "Change stripe account for an user", operationId = "updateAccount", security = @SecurityRequirement(name = "firebase"))
    public Mono<CreateAccountResponseDto> updateAccount(
            Authentication authentication
    ) {
        log.info("updateAccount: userKey: {}", authentication.getName());
        return stripeAccountService.updateAccount(authentication.getName());
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Stripe account not found"),
            @ApiResponse(responseCode = "400", description = "You cannot delete a stripe account, when you have a group with activated subscription"),
    })
    @Operation(description = "Delete stripe account for an user", operationId = "deleteAccount", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> deleteAccount(
            Authentication authentication
    ) {
        log.info("deleteAccount: userKey: {}", authentication.getName());
        return stripeAccountService.deleteAccount(authentication.getName());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully"),
            @ApiResponse(responseCode = "401", description = "Auth error"),
            @ApiResponse(responseCode = "404", description = "Stripe account not found")
    })
    @Operation(description = "See account info and status in stripe", operationId = "getAccountInto", security = @SecurityRequirement(name = "firebase"))
    public Mono<StripeAccountInfoDto> getAccountInto(
            Authentication authentication
    ) {
        log.info("getAccountInto: userKey: {}", authentication.getName());
        return stripeAccountService.getAccountInto(authentication.getName());
    }
}
