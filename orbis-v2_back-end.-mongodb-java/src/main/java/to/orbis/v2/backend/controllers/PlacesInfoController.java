package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.Range;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.openstreetmap.PlaceInfoDto;
import to.orbis.v2.backend.services.PlacesInfoService;

@RestController
@RequestMapping(value = "/places-info", produces = "application/json")
@RequiredArgsConstructor
public class PlacesInfoController {

    PlacesInfoService placesInfoService;

    @GetMapping
    public Mono<PlaceInfoDto> findPlace(
            @RequestParam @Validated @Range(min = -90, max = 90) Double latitude,
            @RequestParam @Validated @Range(min = -180, max = 180) Double longitude
    ) {
        return placesInfoService.findPlace(latitude, longitude);
    }
}
