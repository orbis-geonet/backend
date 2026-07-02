package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.ForwardToNodeJsException;
import to.orbis.v2.backend.mappers.UserMapper;
import to.orbis.v2.backend.models.Language;
import to.orbis.v2.backend.models.dto.UserDto;
import to.orbis.v2.backend.models.requests.users.ChangePasswordRequest;
import to.orbis.v2.backend.models.requests.users.ForgotPasswordRequest;
import to.orbis.v2.backend.models.requests.users.LoginRequest;
import to.orbis.v2.backend.models.requests.users.UserSignupRequest;
import to.orbis.v2.backend.services.FirebaseAuthService;
import to.orbis.v2.backend.services.UsersService;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
public class AuthController {

    UsersService usersService;
    FirebaseAuthService firebaseAuthService;
    UserMapper userMapper;
    ReactiveMongoTemplate mongoTemplate;
    ObjectMapper objectMapper;

    @PostMapping("/login")
    public Mono<UserDto> login(
            @Validated @RequestBody LoginRequest loginRequest,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {
        return getNetworkEventIdByAuthorHash(loginRequest.getEmail(), "users", javaProxied)
                .flatMap(eventId -> {
                    try {
                        String bodyJson = objectMapper.writeValueAsString(loginRequest);
                        return Mono.<UserDto>error(new ForwardToNodeJsException(eventId, bodyJson));
                    } catch (Exception e) {
                        return Mono.<UserDto>error(new ForwardToNodeJsException(eventId));
                    }
                })
                .switchIfEmpty(usersService
                        .authenticateUser(loginRequest.getEmail(), loginRequest.getPassword())
                        .map(userMapper::userToUserDto)
                        .onErrorResume(AuthenticationCredentialsNotFoundException.class,
                                ex -> {
                                    try {
                                        String bodyJson = objectMapper.writeValueAsString(loginRequest);
                                        return Mono.error(new ForwardToNodeJsException(null, bodyJson));
                                    } catch (Exception e) {
                                        return Mono.error(new ForwardToNodeJsException());
                                    }
                                }));
    }

    @PostMapping("/signup")
    public Mono<UserDto> register(
            @Validated @RequestBody UserSignupRequest userSignupRequest,
            @RequestParam(name = "_java_proxied", required = false, defaultValue = "false") boolean javaProxied) {
        return usersService
                .registerUser(userMapper
                        .userSignupRequestToUser(userSignupRequest)
                        .setTimestamp(Instant.now())
                        .setCreateTimestamp(Instant.now())
                        .setActiveServerTimestamp(Instant.now())
                        .setLanguage(Language.EN.name().toLowerCase(Locale.ROOT)),
                        userSignupRequest.getPassword())
                .map(userMapper::userToUserDto);
    }

    @PostMapping("/refresh")
    public Mono<String> refresh(@RequestBody String refreshToken) {
        return firebaseAuthService.refresh(refreshToken);
    }

    @PostMapping("/password")
    @PreAuthorize("isAuthenticated")
    public Mono<String> changePassword(@RequestBody ChangePasswordRequest password, Authentication authentication) {
        return firebaseAuthService.changePassword(authentication.getName(), password);
    }

    @PostMapping("/forgotPassword")
    public Mono<String> forgotPassword(@RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        return firebaseAuthService.forgotPassword(forgotPasswordRequest);
    }

    private Mono<String> getNetworkEventIdByAuthorHash(String email, String collectionName, boolean javaProxied) {
        if (javaProxied || email == null) {
            return Mono.empty();
        }
        String hash = authorHashEncode(email);
        log.info("Checking network_events for authorHash: {} (email: {})", hash, email);

        Query query = Query.query(Criteria.where("collectionName").is(collectionName)
                .and("status").is("pending")
                .and("authorHash").is(hash))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        return mongoTemplate.find(query, org.bson.Document.class, "network_events")
                .next()
                .map(doc -> doc.getObjectId("_id").toHexString());
    }

    private String authorHashEncode(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Error hashing email", e);
            return "";
        }
    }

    private Mono<String> getNetworkEventIdByCollection(String collectionName, boolean javaProxied) {
        if (javaProxied) {
            return Mono.empty();
        }
        Query query = Query.query(Criteria.where("collectionName").is(collectionName).and("status")
                .is("pending"))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        return mongoTemplate.find(query, org.bson.Document.class, "network_events")
                .next()
                .map(doc -> doc.getObjectId("_id").toHexString())
                .switchIfEmpty(Mono.empty());
    }
}
