package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.crypto.CryptoPaymentStatusDto;
import to.orbis.v2.backend.services.CryptoPaymentIntentClient;
import to.orbis.v2.backend.services.CryptoPaymentService;

@Slf4j
@RestController
@RequestMapping("/profile/crypto")
@RequiredArgsConstructor
public class CryptoPaymentStatusController {
    private final CryptoPaymentIntentClient cryptoPaymentIntentClient;
    private final CryptoPaymentService cryptoPaymentService;

    @PreAuthorize("isAuthenticated")
    @GetMapping("/payment-status/{ref}")
    public Mono<CryptoPaymentStatusDto> paymentStatus(@PathVariable String ref, Authentication authentication) {
        log.info("crypto payment-status: ref={}, userKey={}", ref, authentication.getName());
        return cryptoPaymentIntentClient.getStatus(ref)
                .flatMap(status -> cryptoPaymentService.resolvePaymentStatus(status, authentication.getName()));
    }
}
