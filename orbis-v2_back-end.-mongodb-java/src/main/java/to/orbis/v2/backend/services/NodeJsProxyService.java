package to.orbis.v2.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class NodeJsProxyService {

    private final WebClient webClient;
    private final String nodeJsWorkerUrl;
    private final String orbisApiSecret;

    public NodeJsProxyService(WebClient.Builder webClientBuilder,
            @Value("${nodejs.worker.url}") String nodeJsWorkerUrl,
            @Value("${orbis.api.secret}") String orbisApiSecret) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(-1))
                .build();

        this.webClient = webClientBuilder.clone()
                .exchangeStrategies(strategies)
                .build();
        this.nodeJsWorkerUrl = nodeJsWorkerUrl;
        this.orbisApiSecret = orbisApiSecret;
    }

    public Mono<ResponseEntity<byte[]>> forwardRequest(ServerWebExchange exchange) {
        return forwardRequest(exchange, null, null);
    }

    public Mono<ResponseEntity<byte[]>> forwardRequest(ServerWebExchange exchange, String networkEventId) {
        return forwardRequest(exchange, networkEventId, null);
    }

    public Mono<ResponseEntity<byte[]>> forwardRequest(ServerWebExchange exchange, String networkEventId, String bodyJson) {
        return exchange.getPrincipal()
                .map(principal -> principal.getName())
                .defaultIfEmpty("")
                .flatMap(userKey -> forwardRequest(exchange, networkEventId, bodyJson, userKey));
    }

    private Mono<ResponseEntity<byte[]>> forwardRequest(ServerWebExchange exchange, String networkEventId, String bodyJson,
            String userKey) {
        ServerHttpRequest request = exchange.getRequest();

        try {
            URI originalUri = request.getURI();
            String query = originalUri.getRawQuery();
            String extraParams = "_internal=true&_java_proxied=true";
            if (networkEventId != null && !networkEventId.isBlank()) {
                extraParams += "&network_event_id=" + networkEventId;
            }
            String pathAndQuery = originalUri.getRawPath()
                    + (query != null ? "?" + query + "&" + extraParams : "?" + extraParams);
            URI targetUri = new URI(nodeJsWorkerUrl + pathAndQuery);

            HttpMethod method = request.getMethod() != null ? request.getMethod() : HttpMethod.GET;

            log.debug("Proxying request {} to NodeJS worker at {}", pathAndQuery, targetUri);

            WebClient.RequestBodySpec spec = webClient.method(method)
                    .uri(targetUri)
                    .headers(httpHeaders -> {
                        httpHeaders.putAll(request.getHeaders());
                        httpHeaders.remove("Host");
                        httpHeaders.remove("Content-Length");
                        httpHeaders.remove("Transfer-Encoding");
                        httpHeaders.set("X-Master-Key", orbisApiSecret);
                        if (userKey != null && !userKey.isBlank() && !"anonymousUser".equals(userKey)) {
                            httpHeaders.set("X-User-Key", userKey);
                        }
                    });

            WebClient.RequestHeadersSpec<?> readySpec;
            if (bodyJson != null) {
                readySpec = spec
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(bodyJson.getBytes(StandardCharsets.UTF_8));
            } else {
                readySpec = spec.body(request.getBody(), DataBuffer.class);
            }

            return readySpec
                    .exchangeToMono(response -> response.toEntity(byte[].class))
                    .onErrorResume(e -> {
                        log.error("Failed to proxy request to NodeJS worker: ", e);
                        return Mono
                                .just(ResponseEntity.status(502).body("Bad Gateway - NodeJS worker failed".getBytes()));
                    });
        } catch (URISyntaxException e) {
            log.error("Invalid URI syntax when proxying to NodeJS", e);
            return Mono.just(ResponseEntity.status(500).build());
        }
    }
}
