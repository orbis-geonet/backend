package to.orbis.v2.backend.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import to.orbis.v2.backend.models.MyGeoConverters;

import java.util.Collections;

@Configuration
public class MongoConfiguration {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {

        return new MongoCustomConversions(Collections.singletonList(MyGeoConverters.DocumentToGeoJsonMultiPointConverter.INSTANCE));
    }
}
