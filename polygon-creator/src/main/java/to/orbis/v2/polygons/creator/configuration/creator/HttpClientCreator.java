package to.orbis.v2.polygons.creator.configuration.creator;

import com.google.appengine.repackaged.org.apache.http.impl.client.CloseableHttpClient;
import com.google.appengine.repackaged.org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientCreator {

    @Bean
    public CloseableHttpClient createHttpClient() {
        return HttpClientBuilder.create()
                .build();
    }
}
