package to.orbis.v2.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.crypto.PayoutWalletDto;
import to.orbis.v2.backend.services.PayoutWalletService;

@Slf4j
@Validated
@RestController
@RequestMapping("/payout-wallet")
@RequiredArgsConstructor
public class PayoutWalletController {
    private final PayoutWalletService payoutWalletService;

    @PreAuthorize("isAuthenticated")
    @GetMapping
    @Operation(description = "Get the current user's ORBIS payout wallet", operationId = "getPayoutWallet", security = @SecurityRequirement(name = "firebase"))
    public Mono<PayoutWalletDto> getPayoutWallet(Authentication authentication) {
        log.info("getPayoutWallet: userKey: {}", authentication.getName());
        return payoutWalletService.getWallet(authentication.getName());
    }

    @PreAuthorize("isAuthenticated")
    @PostMapping
    @Operation(description = "Set the current user's ORBIS payout wallet", operationId = "setPayoutWallet", security = @SecurityRequirement(name = "firebase"))
    public Mono<PayoutWalletDto> setPayoutWallet(@RequestBody PayoutWalletDto body, Authentication authentication) {
        log.info("setPayoutWallet: userKey: {}", authentication.getName());
        return payoutWalletService.setWallet(authentication.getName(), body.getSolanaPubkey());
    }

    @PreAuthorize("isAuthenticated")
    @DeleteMapping
    @Operation(description = "Delete the current user's ORBIS payout wallet", operationId = "deletePayoutWallet", security = @SecurityRequirement(name = "firebase"))
    public Mono<Void> deletePayoutWallet(Authentication authentication) {
        log.info("deletePayoutWallet: userKey: {}", authentication.getName());
        return payoutWalletService.deleteWallet(authentication.getName());
    }
}
