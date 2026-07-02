package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.services.PlacesService;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/_ah")
public class WarmupController {

    PlacesService placesService;

    @GetMapping("/warmup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> warmup() {
        return placesService.findPlacesForMap(
                new GeoJsonPoint(-46.67, -23.55),
                50.0,
                Optional.empty(),
                Optional.empty(),
                PageRequest.of(0, 25)
        ).then();
    }
}
