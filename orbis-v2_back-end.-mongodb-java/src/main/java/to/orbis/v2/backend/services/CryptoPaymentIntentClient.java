package to.orbis.v2.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.crypto.CryptoPaymentDto;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CryptoPaymentIntentClient {
    private final WebClient webClient;
    private final String masterKey;

    public CryptoPaymentIntentClient(
            @Value("${NODEJS_WORKER_URL:http://localhost:3000}") String cloneProxyUrl,
            @Value("${ORBIS_API_SECRET}") String masterKey
    ) {
        this.webClient = WebClient.builder().baseUrl(cloneProxyUrl).build();
        this.masterKey = masterKey;
    }

    public Mono<CryptoPaymentDto> createIntent(
            String userKey,
            String kind,
            String itemKey,
            Integer quantity,
            String payerWallet,
            String ownerWallet,
            double priceUsd
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("userKey", userKey == null ? "" : userKey);
        body.put("kind", kind);
        body.put("itemKey", itemKey == null ? "" : itemKey);
        body.put("quantity", quantity == null ? 1 : quantity);
        body.put("payerWallet", payerWallet);
        body.put("ownerWallet", ownerWallet);
        body.put("priceUsd", priceUsd);
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/payments/intent").queryParam("_internal", "true").build())
                .header("X-Master-Key", masterKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CryptoPaymentDto.class);
    }
}
