package to.orbis.v2.backend.services;

import com.google.code.geocoder.model.GeocoderRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.NearbySearchRequest;
import com.google.maps.PlaceDetailsRequest;
import com.google.maps.model.*;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.mappers.GooglePlaceMapper;
import to.orbis.v2.backend.models.entity.Place;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

import static com.google.maps.model.AddressType.ROUTE;
import static com.google.maps.model.AddressType.STREET_ADDRESS;

@Service
@Slf4j
public class GooglePlaceService {
    Executor executor = Executors.newCachedThreadPool();
    GeoApiContext geoApiContext;
    GooglePlaceMapper mapper;
    boolean onePage;

    public GooglePlaceService(
            GeoApiContext geoApiContext,
            GooglePlaceMapper mapper,
            @Value("${googlePlaces.onePage:true}") boolean onePage
    ) {
        this.geoApiContext = geoApiContext;
        this.mapper = mapper;
        this.onePage = onePage;
    }

    public Flux<Place> findPlace(GeoJsonPoint location, String type) {
        return findPlaces(
                Optional.of(location),
                Optional.empty(),
                10,
                it -> Arrays.asList(it.types).contains(type)
        );
    }

    public Flux<Place> findPlaces(
            Optional<GeoJsonPoint> location,
            Optional<String> name,
            int distance,
            Predicate<PlacesSearchResult> filter
    ) {
        val loc = location.get();
        return Flux.<PlacesSearchResult>create(placeFluxSink -> executor.execute(() -> {
            val queue = new LinkedBlockingQueue<PlacesSearchResult>();
            Optional<String> nextPage = Optional.empty();
            while (!placeFluxSink.isCancelled()) {

                if (!queue.isEmpty()) {
                    placeFluxSink.next(queue.poll());
                    continue;
                }

                if (nextPage == null) {
                    placeFluxSink.complete();
                    return;
                }

                if (nextPage.isPresent()) {
                    if (onePage) {
                        log.trace("googlePlaces.onePage is true. Skipping further pages");
                        placeFluxSink.complete();
                        return;
                    }
                    // need to wait for nextPageToken to become active
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                val query = new NearbySearchRequest(geoApiContext).location(new LatLng(loc.getY(), loc.getX()))
                        .radius(distance).rankby(RankBy.PROMINENCE);
                val withName = name.map(query::keyword).orElse(query);
                val withNextPage = nextPage.map(query::pageToken).orElse(withName);
                try {
                    val res = withNextPage.await();
                    if (res.nextPageToken == null) {
                        nextPage = null;
                    } else {
                        nextPage = Optional.of(res.nextPageToken);
                    }

                    Arrays.stream(res.results).forEach(queue::offer);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable e) {
                    placeFluxSink.error(e);
                    return;
                }
            }
        }))
                .filter(filter)
                .map(mapper::googlePlaceToPlace);
    }

    public Mono<Place> findPlaceByID(String googlePlaceId) {
        return Mono.<PlaceDetails>create(sink -> {
            var query = new PlaceDetailsRequest(geoApiContext).placeId(googlePlaceId);
            try {
                val res = query.await();
                sink.success(res);
            } catch (Throwable e) {
                sink.error(e);
            }
        }).map(mapper::placeDetailsToPlace);
    }

    public Mono<Place> findPlaceAddress(GeoJsonPoint loc) {
        return Mono.<GeocodingResult>create(sink -> {
            try {
                var result = Arrays.stream(GeocodingApi.newRequest(geoApiContext).latlng(new LatLng(loc.getY(), loc.getX())).await())
                        .filter(it -> Arrays.asList(it.types).contains(STREET_ADDRESS) || Arrays.asList(it.types).contains(ROUTE))
                        .findFirst();
                if (result.isPresent()) {
                    sink.success(result.get());
                } else {
                    sink.success();
                }
            } catch (Throwable e) {
                sink.error(e);
            }
        }).map(mapper::geocoderResultToPlace);
    }
}
