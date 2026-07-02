package to.orbis.v2.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@Slf4j
public class FirebaseIndexService {

    WebClient webClient;
    Set<String> indexedHashes;


    public FirebaseIndexService(WebClient.Builder webClientBuilder, @Value("${firebase.databaseUrl}") String databaseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(databaseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        indexedHashes = Collections.synchronizedSet(new HashSet<>());
    }

    ParameterizedTypeReference<Map<String, Object>> mapRef = new ParameterizedTypeReference<>() {
    };

    ParameterizedTypeReference<String> stringRef = new ParameterizedTypeReference<>() {
    };

    @PostConstruct
    public void loadIndexedHashes() {
        String token = authorizeWithFirebase();

        try {
            final Map<String, Object> res = loadRules(token).block();

            final Set<String> knownHashes = parseKnownHashes(res);
            indexedHashes.addAll(knownHashes);
            log.info("Indexed hashes: {}", indexedHashes);
        } catch (Throwable err) {
            log.error("Failed to load known hashes.", err);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseKnownHashes(Map<String, Object> res) {
        if (res == null) {
            return Collections.emptySet();
        }

        Map<String, Object> rules = (Map<String, Object>) res.get("rules");
        Map<String, Object> placeSizes = (Map<String, Object>) rules.get("placeSizes");
        return placeSizes.keySet();
    }

    private Mono<Map<String, Object>> loadRules(String token) {
        return webClient.get().uri(b -> b.path("/.settings/rules.json").queryParam("access_token", token).build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(mapRef);
    }

    public Mono<Void> ensureIndex(String geohash) {
        if (indexedHashes.contains(geohash)) {
            return Mono.empty();
        }

        String token = authorizeWithFirebase();
        return loadRules(token)
                .flatMap(rules -> {
                    val currentHashes = parseKnownHashes(rules);

                    indexedHashes.addAll(currentHashes);
                    if (indexedHashes.contains(geohash)) {
                        return Mono.empty();
                    }

                    return Mono.just(insertIndexingRule(rules, geohash));
                })
                .flatMap(stringObjectMap -> writeRules(token, stringObjectMap))
                .doOnSuccess(_ignored -> indexedHashes.add(geohash))
                .onErrorResume(t -> {
                    log.error("Failed to update rules: ", t);
                    return Mono.empty();
                });
    }

    private Mono<Void> writeRules(String token, Map<String, Object> newRules) {
        return webClient.put().uri(b -> b.path("/.settings/rules.json").queryParam("access_token", token).build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(newRules))
                .retrieve()
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse
                                .bodyToMono(stringRef)
                                .flatMap(e -> Mono.error(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e))))
                .bodyToMono(mapRef)
                .then();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> insertIndexingRule(Map<String, Object> rulesObject, String geohash) {
        Map<String, Object> rules = (Map<String, Object>) rulesObject.get("rules");
        Map<String, Object> placeSizes = (Map<String, Object>) rules.get("placeSizes");
        Map<String, Object> groupOwnership = (Map<String, Object>) rules.get("groupOwnership");

        placeSizes.put(geohash, createIndexBy("lastSizeChangeTimestamp"));
        groupOwnership.put(geohash, createIndexBy("timestamp"));

        return rulesObject;
    }

    private Object createIndexBy(String fieldName) {
        val indexBy = new HashMap<String, Object>();
        indexBy.put(".indexOn", List.of(fieldName));
        return indexBy;
    }

    public Set<String> getIndexedHashes() {
        return Set.copyOf(indexedHashes);
    }

    @SuppressWarnings("deprecation")
    @SneakyThrows
    private String authorizeWithFirebase() {
        GoogleCredential googleCred = GoogleCredential.getApplicationDefault();

        // GoogleCredential.getApplicationDefault()

        // Add the required scopes to the Google credential
        GoogleCredential scoped = googleCred.createScoped(
                Arrays.asList(
                        "https://www.googleapis.com/auth/firebase.database",
                        "https://www.googleapis.com/auth/userinfo.email"
                )
        );

        // Use the Google credential to generate an access token
        scoped.refreshToken();
        return scoped.getAccessToken();
    }

}
