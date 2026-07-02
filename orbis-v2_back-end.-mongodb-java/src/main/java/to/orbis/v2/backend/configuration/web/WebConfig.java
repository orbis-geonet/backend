package to.orbis.v2.backend.configuration.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/simulation/v1/**")
                .addResourceLocations("classpath:/simulation/v1/");

        registry.addResourceHandler("/simulation/v2/**")
                .addResourceLocations("classpath:/simulation/v2/");
    }
}
