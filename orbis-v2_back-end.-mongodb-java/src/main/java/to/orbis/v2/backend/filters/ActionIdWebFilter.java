package to.orbis.v2.backend.filters;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ActionIdWebFilter implements WebFilter {

    public static final String ACTION_ID_HEADER = "X-Orbis-Action-Id";
    public static final String ACTION_ID_CONTEXT_KEY = "orbisActionId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String provided = exchange.getRequest().getHeaders().getFirst(ACTION_ID_HEADER);
        String actionId = (provided != null && !provided.isBlank()) ? provided : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().add(ACTION_ID_HEADER, actionId);
        return chain.filter(exchange).contextWrite(ctx -> ctx.put(ACTION_ID_CONTEXT_KEY, actionId));
    }
}
