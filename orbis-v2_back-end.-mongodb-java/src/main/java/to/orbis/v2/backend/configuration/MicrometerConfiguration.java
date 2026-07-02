package to.orbis.v2.backend.configuration;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNullApi;

import java.util.stream.Collectors;

@Configuration
public class MicrometerConfiguration {

    @Bean(name = "httpClientSuppressApiKey")
    public MeterFilter httpClientSuppressApiKey() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if(id.getName().startsWith("http.client")) {
                    return id.replaceTags(id.getTags().stream().map(t -> {
                        if(t.getKey().equals("uri") && t.getValue().contains("?")) {
                            return Tag.of("uri", t.getValue().substring(0, t.getValue().indexOf("?")));
                        }
                        return t;
                    }).collect(Collectors.toList()));
                }
                return id;
            }
        };
    }
}
