package to.orbis.v2.backend.filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import to.orbis.v2.backend.models.dto.PointDto;
import to.orbis.v2.backend.services.UsersService;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LocationWebFilter implements WebFilter {

    UsersService usersService;

    public static final String COORDS_HEADER = "Orbis-Coordinates";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        return exchange.getPrincipal().map(p -> {
            if (p instanceof AnonymousAuthenticationToken) {
                log.debug("Anonymous user, skipping updating location");
                return p;
            }
            log.debug("Got principal: {}, checking header", p.getName());
            if (exchange.getRequest().getHeaders().containsKey(COORDS_HEADER)) {
                updateLocation(p.getName(), exchange.getRequest().getHeaders().get(COORDS_HEADER));
            }
            return p;
        }).flatMap(p -> chain.filter(exchange));
    }

    private void updateLocation(String userKey, List<String> locationHeaders) {
        if (locationHeaders == null || locationHeaders.isEmpty()) {
            log.warn("{} header is somehow renders empty list", COORDS_HEADER);
            return;
        }

        val coords = locationHeaders.get(0);

        GeoJsonPoint point = parseLocationHeader(coords);
        if (point == null) return;

        usersService.updateLocation(userKey, point)
                .subscribeOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.boundedElastic())
                .subscribe(_ignored -> {}, err -> log.error("Failed to update user {} location to {}", userKey, point, err));
    }

    public static GeoJsonPoint parseLocationHeader(String coords) {
        val longLat = coords.split(";");

        if (longLat.length != 2) {
            log.warn("Incorrect {} header format. Must be <longitude>;<latitude>", COORDS_HEADER);
            return null;
        }

        double lon = Double.parseDouble(longLat[0]);
        double lat = Double.parseDouble(longLat[1]);

        if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            log.warn("Latitude or longitude in {} header is out of bounds", COORDS_HEADER);
            return null;
        }

        return new GeoJsonPoint(lon, lat);
    }
}
