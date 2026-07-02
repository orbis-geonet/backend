package to.orbis.v2.backend.services;

import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.OpenMapConfiguration;
import to.orbis.v2.backend.mappers.OpenMapMapper;
import to.orbis.v2.backend.models.dto.openstreetmap.OpenMapPlace;
import to.orbis.v2.backend.models.dto.openstreetmap.PhotonPlace;
import to.orbis.v2.backend.models.dto.openstreetmap.PlaceInfoDto;
import java.util.Optional;

@Service
public class PlacesInfoService {
    OpenMapConfiguration openMapConfiguration;
    OpenMapMapper openMapMapper;
    WebClient webClient;

    public PlacesInfoService(OpenMapMapper openMapMapper, OpenMapConfiguration openMapConfiguration, WebClient.Builder builder) {
        this.openMapConfiguration = openMapConfiguration;
        this.openMapMapper = openMapMapper;

        // Caused by: reactor.netty.http.client.PrematureCloseException: Connection prematurely closed BEFORE response
        this.webClient = builder
                .defaultHeader("User-Agent", "OrbisApp/2.0 (orbis.social)")
                .defaultHeader("Referer", "https://orbis.social")
                .build();
    }

    public Mono<String> findCityByCoordinates(GeoJsonPoint coordinates) {
        return findPlace(coordinates.getY(), coordinates.getX())
                .map(place ->
                        Optional.ofNullable(place.getCity())
                                .or(() -> Optional.ofNullable(place.getCounty()))
                                .or(() -> Optional.ofNullable(place.getCountry()))
                                .orElse(null)
                );
    }

    public Mono<PlaceInfoDto> findPlace(Double latitude, Double longitude) {
        var url = UriComponentsBuilder.fromHttpUrl(openMapConfiguration.getSearchUrl())
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .build()
                .toUri();

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(PhotonPlace.class)
                .map(r -> r.getFeatures().get(0).getProperties())
                .map(openMapMapper::toPlaceInfo)
                .retry(5);
    }
}
