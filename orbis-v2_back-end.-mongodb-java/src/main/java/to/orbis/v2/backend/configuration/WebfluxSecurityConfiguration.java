package to.orbis.v2.backend.configuration;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.security.AccessDeniedHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class WebfluxSecurityConfiguration {
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, AccessDeniedHandler accessDeniedHandler) {
        http
                .authorizeExchange()
                .anyExchange().permitAll()
                .and()
                .httpBasic().disable()
                .formLogin().disable()
                .anonymous().and()
                .cors().disable()
                .csrf().disable()
                .oauth2ResourceServer()
                .jwt()
                .and().accessDeniedHandler(accessDeniedHandler)
                .authenticationEntryPoint(accessDeniedHandler);

        return http.build();
    }

    @Bean
    @SneakyThrows
    public RSASSAVerifier verifier() {
        try (val pk = this.getClass().getResourceAsStream("/pk.rsa");
             val r = new BufferedReader(new InputStreamReader(pk))) {
            return new RSASSAVerifier(RSAKey.parse(r.lines().collect(Collectors.joining())));
        }
    }

    private Mono<Void> authEntryPoint(ServerWebExchange exchange, AuthenticationException e) {
        return null;
    }

}
