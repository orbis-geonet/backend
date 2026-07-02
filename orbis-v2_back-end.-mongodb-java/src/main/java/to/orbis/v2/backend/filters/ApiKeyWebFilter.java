package to.orbis.v2.backend.filters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class ApiKeyWebFilter implements WebFilter {

    private final String orbisApiSecret;

    public ApiKeyWebFilter(@Value("${ORBIS_API_SECRET}") String orbisApiSecret) {
        this.orbisApiSecret = orbisApiSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        if (apiKey != null && isValidHmac(apiKey)) {
            return chain.filter(exchange);
        }

        String masterKey = exchange.getRequest().getHeaders().getFirst("X-Master-Key");
        if (masterKey != null && masterKey.equals(orbisApiSecret)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap("{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private boolean isValidHmac(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) return false;
            String payload = parts[0];
            String signature = parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(orbisApiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
