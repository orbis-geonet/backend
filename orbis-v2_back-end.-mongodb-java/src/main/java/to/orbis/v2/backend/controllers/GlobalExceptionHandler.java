package to.orbis.v2.backend.controllers;

import com.google.firebase.auth.FirebaseAuthException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import to.orbis.v2.backend.models.dto.ErrorDto;

import java.util.stream.Collectors;

@Component
@ControllerAdvice
@Slf4j
@Order(-2)
@lombok.RequiredArgsConstructor
public class GlobalExceptionHandler implements WebExceptionHandler {

    private final to.orbis.v2.backend.services.NodeJsProxyService nodeJsProxyService;

    @ExceptionHandler
    public ResponseEntity<ErrorDto> handleValidationException(WebExchangeBindException validationException) {
        return ResponseEntity.badRequest()
                .body(getErrorDto(validationException));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorDto> handleAuthException(AuthenticationException authenticationException) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(getErrorDto(authenticationException));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorDto> handleAuthException(AccessDeniedException accessDeniedException) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(getErrorDto(accessDeniedException));
    }

    @ExceptionHandler(Throwable.class)
    public reactor.core.publisher.Mono<ResponseEntity<?>> handleGenericException(Throwable ex,
            org.springframework.web.server.ServerWebExchange exchange) {
        log.error("Internal error or unhandled exception: {}.", ex.getMessage(), ex);
        return reactor.core.publisher.Mono
                .just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(getErrorDto(ex)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public reactor.core.publisher.Mono<ResponseEntity<?>> handleResponseStatusException(ResponseStatusException ex,
            org.springframework.web.server.ServerWebExchange exchange) {
        if (ex.getStatus() == HttpStatus.NOT_FOUND) {
            if ("true".equals(exchange.getRequest().getQueryParams().getFirst("_java_proxied"))) {
                log.warn("Prevented infinite proxy loop for {} on internal proxied request", ex.getStatus());
                return reactor.core.publisher.Mono
                        .just(ResponseEntity.status(ex.getRawStatusCode()).body(getErrorDto(ex)));
            }
            log.debug("Forwarding ResponseStatusException (status: {}) to NodeJS worker", ex.getStatus());
            return nodeJsProxyService.forwardRequest(exchange).map(re -> (ResponseEntity<?>) re);
        }
        return reactor.core.publisher.Mono.just(ResponseEntity.status(ex.getRawStatusCode())
                .body(getErrorDto(ex)));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorDto> handleResponseStatusException(FirebaseAuthException firebaseAuthException) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value())
                .body(getErrorDto(firebaseAuthException));
    }

    @ExceptionHandler({ to.orbis.v2.backend.exceptions.NoDataFoundException.class,
            to.orbis.v2.backend.exceptions.ForwardToNodeJsException.class })
    public reactor.core.publisher.Mono<ResponseEntity<?>> handleNodeJsForwarding(Exception ex,
            org.springframework.web.server.ServerWebExchange exchange) {
        if ("true".equals(exchange.getRequest().getQueryParams().getFirst("_java_proxied"))) {
            log.warn("Prevented infinite proxy loop for NoDataFound/ForwardToNodeJs on internal proxied request");
            return reactor.core.publisher.Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorDto.builder().code(404).message(ex.getMessage() != null ? ex.getMessage() : "No data found")
                            .build()));
        }
        log.debug("Forwarding specific exception to NodeJS worker: {}", ex.getMessage());
        String networkEventId = null;
        String bodyJson = null;
        if (ex instanceof to.orbis.v2.backend.exceptions.ForwardToNodeJsException) {
            networkEventId = ((to.orbis.v2.backend.exceptions.ForwardToNodeJsException) ex).getNetworkEventId();
            bodyJson = ((to.orbis.v2.backend.exceptions.ForwardToNodeJsException) ex).getBodyJson();
        }
        return nodeJsProxyService.forwardRequest(exchange, networkEventId, bodyJson).map(re -> (ResponseEntity<?>) re);
    }

    private ErrorDto getErrorDto(WebExchangeBindException bindException) {
        String errorMsg = bindException.getBindingResult().getFieldErrors().stream()
                .map(br -> String.format("%s: %s", br.getField(), br.getDefaultMessage()))
                .collect(Collectors.joining("\n"));

        return ErrorDto.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message(errorMsg.isBlank() ? bindException.getMessage() : errorMsg)
                .build();
    }

    private ErrorDto getErrorDto(AuthenticationException ex) {
        return ErrorDto.builder()
                .code(HttpStatus.UNAUTHORIZED.value())
                .message(ex.getMessage())
                .build();
    }

    private ErrorDto getErrorDto(AccessDeniedException ex) {
        return ErrorDto.builder()
                .code(HttpStatus.FORBIDDEN.value())
                .message(ex.getMessage())
                .build();
    }

    private ErrorDto getErrorDto(FirebaseAuthException ex) {
        return ErrorDto.builder()
                .code(HttpStatus.UNAUTHORIZED.value())
                .message(ex.getMessage())
                .build();
    }

    private ErrorDto getErrorDto(ResponseStatusException ex) {
        val cause = findCause(ex, false);
        if (cause != null) {
            ex = cause;
        }
        return ErrorDto.builder()
                .code(ex.getRawStatusCode())
                .message(ex.getReason())
                .build();
    }

    private ResponseStatusException findCause(Throwable ex, boolean acceptSelf) {
        if (ex instanceof ResponseStatusException && acceptSelf)
            return (ResponseStatusException) ex;
        if (ex.getCause() != null)
            return findCause(ex.getCause(), true);
        return null;
    }

    private ErrorDto getErrorDto(Throwable ex) {
        return ErrorDto.builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(ex.getMessage())
                .build();
    }

    @Override
    public reactor.core.publisher.Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof ResponseStatusException
                && ((ResponseStatusException) ex).getStatus() == HttpStatus.NOT_FOUND) {
            if ("true".equals(exchange.getRequest().getQueryParams().getFirst("_java_proxied"))) {
                log.warn("Prevented infinite proxy loop for global 404 NOT_FOUND on internal proxied request");
                return reactor.core.publisher.Mono.error(ex);
            }
            log.debug("Caught global 404 NOT_FOUND for {}. Forwarding to NodeJS worker.",
                    exchange.getRequest().getURI().getPath());
            return nodeJsProxyService.forwardRequest(exchange)
                    .flatMap(re -> {
                        exchange.getResponse().setStatusCode(re.getStatusCode());
                        exchange.getResponse().getHeaders().addAll(re.getHeaders());
                        if (re.getBody() != null) {
                            return exchange.getResponse().writeWith(reactor.core.publisher.Mono
                                    .just(exchange.getResponse().bufferFactory().wrap(re.getBody())));
                        }
                        return exchange.getResponse().setComplete();
                    });
        }
        return reactor.core.publisher.Mono.error(ex);
    }
}
