package to.orbis.v2.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.ErrorDto;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class AccessDeniedHandler implements ServerAccessDeniedHandler, ServerAuthenticationEntryPoint {

    ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return Mono.defer(() -> Mono.just(exchange.getResponse())).flatMap((response) -> {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBufferFactory dataBufferFactory = response.bufferFactory();
            val uri = exchange.getRequest().getURI();
            val errorDto = ErrorDto.builder()
                    .code(HttpStatus.FORBIDDEN.value())
                    .message(deduceMessage(uri, denied.getMessage()))
                    .build();
            DataBuffer buffer = dataBufferFactory.wrap(getBytes(errorDto));
            return response.writeWith(Mono.just(buffer)).doOnError((error) -> DataBufferUtils.release(buffer));
        });

    }

    private String deduceMessage(URI uri, String message) {
        if (uri.getPath().endsWith("/profile/me")) {
            return "You can edit only your own profile";
        }
        return "Access denied";
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return Mono.defer(() -> Mono.just(exchange.getResponse())).flatMap((response) -> {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBufferFactory dataBufferFactory = response.bufferFactory();
            val uri = exchange.getRequest().getURI();
            val errorDto = ErrorDto.builder()
                    .code(HttpStatus.UNAUTHORIZED.value())
                    .message(ex.getMessage())
                    .build();
            DataBuffer buffer = dataBufferFactory.wrap(getBytes(errorDto));
            return response.writeWith(Mono.just(buffer)).doOnError((error) -> DataBufferUtils.release(buffer));
        });
    }

    @SneakyThrows
    protected byte[] getBytes(ErrorDto errorDto) {
        return objectMapper.writeValueAsBytes(errorDto);
    }
}
