package to.orbis.v2.polygons.creator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import to.orbis.v2.polygons.creator.utils.MemoryUtils;

import javax.annotation.PostConstruct;

@Slf4j
@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties
@ConfigurationPropertiesScan
public class PolygonCreatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolygonCreatorApplication.class, args);
    }

    @PostConstruct
    private void memStats() {
        MemoryUtils.printCurrentMemoryInfo("Application start");
    }
}
