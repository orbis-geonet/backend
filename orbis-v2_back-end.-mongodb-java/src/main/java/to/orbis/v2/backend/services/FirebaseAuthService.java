package to.orbis.v2.backend.services;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.FirebaseConfigurationOptions;
import to.orbis.v2.backend.models.entity.Token;
import to.orbis.v2.backend.models.entity.User;
import to.orbis.v2.backend.models.requests.users.ChangePasswordRequest;
import to.orbis.v2.backend.models.requests.users.ForgotPasswordRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class FirebaseAuthService {

    FirebaseConfigurationOptions options;
    WebClient webClient;

    public FirebaseAuthService(FirebaseConfigurationOptions options, WebClient.Builder builder) {
        this.options = options;
        this.webClient = builder
                .baseUrl("https://identitytoolkit.googleapis.com")
                .build();
    }

    Executor executor = Executors.newFixedThreadPool(10);

    public Mono<User> signup(User user, String password) {
        val createRequest = new UserRecord.CreateRequest()
                .setEmail(user.getEmail())
                .setPassword(password)
                .setDisplayName(user.getDisplayName());
        if (user.getProviderImageUrl() != null && !user.getProviderImageUrl().isBlank()) {
            createRequest.setPhotoUrl(user.getProviderImageUrl());
        }
        val async = FirebaseAuth.getInstance().createUserAsync(createRequest);

        final Mono<UserRecord> userRecordMono = Mono.create(sink -> async.addListener(() -> {
            try {
                sink.success(async.get());
            } catch (Exception e) {
                if (e instanceof ExecutionException && e.getCause() != null) {
                    sink.error(e.getCause());
                } else {
                    sink.error(e);
                }
            }
        }, executor));

        return userRecordMono
                .flatMap(userRec -> authenticate(user.getEmail(), password)
                        .map(token -> {
                            val u = user.setTokens(token).setUserKey(userRec.getUid());
                            u.setTimestamp(Instant.now());
                            if (u.getCreateTimestamp() == null) {
                               u.setCreateTimestamp(Instant.now());
                            }
                            return u;
                        }));
    }

    public Mono<Void> deleteUser(String userKey) {
        val async = FirebaseAuth.getInstance().deleteUserAsync(userKey);

        return Mono.create(sink -> async.addListener(() -> {
            try {
                async.get();
                sink.success();
            } catch (Exception e) {
                if (e instanceof ExecutionException && e.getCause() != null) {
                    sink.error(e.getCause());
                } else {
                    sink.error(e);
                }
            }
        }, executor)).then();
    }

    public Mono<String> changePassword(String userKey, ChangePasswordRequest password) {
        return webClient.post()
                .uri(b -> b
                        .path("/v1/accounts:resetPassword")
                        .queryParam("key", options.getApiKey())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(
                        ChangePasswordAuthRequest.builder()
                                .email(password.getEmail())
                                .oldPassword(password.getOldPassword())
                                .newPassword(password.getNewPassword())
                                .build()))
                .retrieve()
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse
                                .bodyToMono(mapRef)
                                .map(this::convertToError))
                .bodyToMono(mapRef)
                .flatMap(m -> Mono.justOrEmpty("changed"))
                .switchIfEmpty(Mono.error(this::tokenNotFound));
    }

    public Mono<String> forgotPassword(ForgotPasswordRequest forgotPasswordRequest) {
        return webClient.post()
                .uri(b -> b
                        .path("/v1/accounts:sendOobCode")
                        .queryParam("key", options.getApiKey())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(
                        ForgotPasswordAuthRequest.builder()
                                .email(forgotPasswordRequest.getEmail())
                                .userIp(forgotPasswordRequest.getUserIp())
                                .build()))
                .retrieve()
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse
                                .bodyToMono(mapRef)
                                .map(this::convertToError))
                .bodyToMono(mapRef)
                .flatMap(m -> Mono.justOrEmpty("reset link sent"))
                .switchIfEmpty(Mono.error(this::tokenNotFound));
    }

    @Builder
    @RequiredArgsConstructor
    @Data
    private static class ForgotPasswordAuthRequest {
        String email;
        String requestType = "PASSWORD_RESET";
        String userIp;
    }

    @Builder
    @RequiredArgsConstructor
    @Data
    private static class ChangePasswordAuthRequest {
        String email;
        String oldPassword;
        String newPassword;
    }

    @Builder
    @RequiredArgsConstructor
    @Data
    private static class AuthRequest {
        String email;
        String password;
        boolean returnSecureToken = true;
    }

    ParameterizedTypeReference<Map<String, Object>> mapRef = new ParameterizedTypeReference<>() {
    };

    public Mono<Token> authenticate(String email, String password) {
        /*
        curl 'https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=[API_KEY]' \
-H 'Content-Type: application/json' \
--data-binary '{"email":"[user@example.com]","password":"[PASSWORD]","returnSecureToken":true}'
         */
        return webClient.post()
                .uri(b -> b
                        .path("/v1/accounts:signInWithPassword")
                        .queryParam("key", options.getApiKey())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(
                        AuthRequest.builder()
                                .email(email)
                                .password(password)
                                .build()))
                .retrieve()
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse
                                .bodyToMono(mapRef)
                                .map(this::convertToError))
                .bodyToMono(mapRef)
                .flatMap(m -> Mono.justOrEmpty(
                        Token.builder()
                                .idToken((String) m.getOrDefault("idToken", null))
                                .refreshToken((String) m.getOrDefault("refreshToken", null))
                                .build()))
                .switchIfEmpty(Mono.error(this::tokenNotFound));
    }

    private Throwable   tokenNotFound() {
        return new AuthenticationServiceException("Token not found in answer");
    }

    @SuppressWarnings("unchecked")
    private Throwable convertToError(Map<String, Object> m) {
        Map<String, Object> error = (Map<String, Object>) m.getOrDefault("error", Collections.emptyMap());
        switch ((String) error.getOrDefault("message", "UNKNOWN")) {
            case "INVALID_EMAIL":
                return new BadCredentialsException("Invalid email");
            case "EMAIL_NOT_FOUND":
            case "INVALID_PASSWORD":
            case "USER_NOT_FOUND":
                return new BadCredentialsException("Bad credentials");
            case "INVALID_REFRESH_TOKEN":
                return new BadCredentialsException("Invalid token");
            case "USER_DISABLED":
                return new DisabledException("User blocked");
        }
        log.error("Authentication failed: {}", m);
        return new AuthenticationServiceException("Unexpected authentication exception");
    }

    public Mono<String> refresh(String refreshToken) {
        /*
        curl 'https://securetoken.googleapis.com/v1/token?key=[API_KEY]' \
-H 'Content-Type: application/x-www-form-urlencoded' \
--data 'grant_type=refresh_token&refresh_token=[REFRESH_TOKEN]'
         */
        return webClient.post()
                .uri(b -> b
                        .path("/v1/token")
                        .queryParam("key", options.getApiKey())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("refresh_token", refreshToken).with("grant_type", "refresh_token"))
                .retrieve()
                .onStatus(HttpStatus::isError,
                        clientResponse -> clientResponse
                                .bodyToMono(mapRef)
                                .map(this::convertToError))
                .bodyToMono(mapRef)
                .flatMap(m -> Mono.justOrEmpty(m.getOrDefault("id_token", null)))
                .cast(String.class)
                .switchIfEmpty(Mono.error(this::tokenNotFound));
    }
}
