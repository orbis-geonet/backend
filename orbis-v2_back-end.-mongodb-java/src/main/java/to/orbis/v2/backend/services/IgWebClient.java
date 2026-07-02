package to.orbis.v2.backend.services;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.IgOptions;
import to.orbis.v2.backend.models.entity.IgLink;
import to.orbis.v2.backend.models.ig.IgAccessToken;
import to.orbis.v2.backend.models.ig.IgMedia;
import to.orbis.v2.backend.models.ig.IgMediaType;
import to.orbis.v2.backend.models.ig.IgResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IgWebClient {

    static final ParameterizedTypeReference<IgResponse<IgMedia>> IG_RESPONSE_MEDIA = new ParameterizedTypeReference<>() {
    };
    IgOptions options;
    WebClient webClient;
    WebClient graphClient;

    public IgWebClient(IgOptions options, WebClient.Builder builder) {
        this.options = options;
        this.webClient = builder
                .baseUrl("https://api.instagram.com")
                .build();

        this.graphClient = builder
                .baseUrl("https://graph.instagram.com")
                .build();
    }

    public String generateAuthUrl(String state) {
        return String.format("https://api.instagram.com/oauth/authorize" +
                "?client_id=%s" +
                "&redirect_uri=%s" +
                "&scope=user_profile,user_media" +
                "&response_type=code" +
                "&state=%s", options.getClientId(), options.getRedirectUri(), state);
    }

    public Mono<IgLink> exchangeCodeForToken(IgLink igLink, String code) {
        /*
          -F client_id={app-id} \
  -F client_secret={app-secret} \
  -F grant_type=authorization_code \
  -F redirect_uri={redirect-uri} \
  -F code={code}
         */

        /*
        {
  "access_token": "IGQVJ...",
  "user_id": 17841405793187218
}
         */
        return webClient
                .post()
                .uri("/oauth/access_token")
                .body(BodyInserters
                        .fromFormData("client_id", options.getClientId())
                        .with("client_secret", options.getSecret())
                        .with("grant_type", "authorization_code")
                        .with("redirect_uri", options.getRedirectUri())
                        .with("code", code))
                .retrieve()
                .bodyToMono(IgAccessToken.class)
                .map(token -> igLink.setToken(token.getAccessToken())
                        .setIgUserId(token.getUserId()));
    }

    public Mono<IgLink> exchangeForLongTermToken(IgLink igLink) {
        /*
        grant_type=ig_exchange_token
  &client_secret={instagram-app-secret}
  &access_token={short-lived-access-token}
         */
        return graphClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/access_token")
                        .queryParam("grant_type", "ig_exchange_token")
                        .queryParam("client_secret", options.getSecret())
                        .queryParam("access_token", igLink.getToken())
                        .build())
                .retrieve()
                .bodyToMono(IgAccessToken.class)
                .map(token -> igLink.setToken(token.getAccessToken())
                        .setExpirationTime(Instant.now().plus(token.getExpiresIn(), ChronoUnit.SECONDS)));
    }

    public Mono<IgResponse<IgMedia>> listMedia(IgLink igLink) {
        return graphClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v11.0/{userId}/media")
                        .queryParam("access_token", igLink.getToken())
                        .queryParam("fields", "caption,id,media_type,media_url,thumbnail_url,timestamp,username")
                        .build(igLink.getIgUserId()))
                .retrieve()
                .bodyToMono(IG_RESPONSE_MEDIA);
    }

    public Mono<IgResponse<IgMedia>> listMedia(String link) {
        final String[] parts = link.split("/", 4);
        val uri = URLDecoder.decode("/" + parts[3], StandardCharsets.UTF_8);
        return graphClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(IG_RESPONSE_MEDIA);
    }

    public Mono<List<String>> getChildrenMedia(IgLink igLink, IgMedia igMedia) {
        return graphClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v11.0/{mediaId}/children")
                        .queryParam("access_token", igLink.getToken())
                        .queryParam("fields", "media_type,media_url")
                        .build(igMedia.getId()))
                .retrieve()
                .bodyToMono(IG_RESPONSE_MEDIA)
                .map(mediaResponse -> mediaResponse.getData().stream()
                        .filter(media -> media.getMediaType() == IgMediaType.IMAGE)
                        .map(IgMedia::getMediaUrl)
                        .collect(Collectors.toList()))
                .map(list -> {
                    if (list.isEmpty()) {
                        return Collections.singletonList(igMedia.getMediaUrl());
                    } else {
                        return list;
                    }
                });

    }

    public Mono<List<String>> getMediaLink(IgLink igLink, String mediaId, String oldUrl) {
        return graphClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{mediaId}")
                        .queryParam("access_token", igLink.getToken())
                        .queryParam("fields", "media_type,media_url")
                        .build(mediaId))
                .retrieve()
                .bodyToMono(IgMedia.class)
                .map(media -> Collections.singletonList(media.getMediaUrl()));
    }
}
