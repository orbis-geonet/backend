package to.orbis.v2.backend.configuration;

import com.google.maps.GeoApiContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GoogleMapsApiConfiguration {

    @Bean(destroyMethod = "shutdown")
    public GeoApiContext geoApiContext(@Value("${firebase.apiKey}") String apiKey) {
        return new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();
    }
}
