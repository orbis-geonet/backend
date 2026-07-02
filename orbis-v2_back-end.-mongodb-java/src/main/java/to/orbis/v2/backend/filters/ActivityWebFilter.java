package to.orbis.v2.backend.filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.services.FirestoreService;
import to.orbis.v2.backend.services.UsersService;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile({"prod", "staging"})
public class ActivityWebFilter implements WebFilter {

    FirestoreService fireStoreService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        return exchange.getPrincipal().map(p -> {
            if (p instanceof AnonymousAuthenticationToken) {
                log.debug("Anonymous user, skipping updating location");
                return p;
            }
            log.debug("Got principal: {}, checking header", p.getName());
            fireStoreService.updateLastActive(p.getName());
            return p;
        }).flatMap(p -> chain.filter(exchange));
    }
}
