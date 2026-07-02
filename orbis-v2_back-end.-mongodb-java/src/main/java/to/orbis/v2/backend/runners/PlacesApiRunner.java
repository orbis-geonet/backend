package to.orbis.v2.backend.runners;

import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlacesSearchResult;
import com.google.maps.model.RankBy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("placesrunner")
public class PlacesApiRunner implements CommandLineRunner {

    GeoApiContext geoApiContext;

    @Override
    public void run(String... args) throws Exception {
        if (1 == 1) return;

        log.info("Querying for places");
        val searchResponse = PlacesApi.nearbySearchQuery(geoApiContext, new LatLng(52.6401632, 4.7424061)).keyword("ca").radius(2000).rankby(RankBy.PROMINENCE).await();
        log.info("Next page: {}", searchResponse.nextPageToken);
        for (PlacesSearchResult result : searchResponse.results) {
            log.info("Found place: {}", result);
        }
    }
}
