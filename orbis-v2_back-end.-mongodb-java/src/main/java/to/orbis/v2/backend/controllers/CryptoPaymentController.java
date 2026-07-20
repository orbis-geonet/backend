package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.crypto.PaymentConfirmedDto;
import to.orbis.v2.backend.services.CryptoPaymentService;

@Slf4j
@RestController
@RequestMapping("/internal/crypto")
@RequiredArgsConstructor
public class CryptoPaymentController {
    private final CryptoPaymentService cryptoPaymentService;

    @PostMapping("/payment-confirmed")
    public Mono<ResponseEntity<Void>> paymentConfirmed(@RequestBody PaymentConfirmedDto body) {
        log.info("crypto payment-confirmed: ref={}, tx={}", body.getRef(), body.getTxSignature());
        return cryptoPaymentService.confirmPayment(body.getRef(), body.getTxSignature())
                .thenReturn(ResponseEntity.ok().<Void>build());
    }
}
