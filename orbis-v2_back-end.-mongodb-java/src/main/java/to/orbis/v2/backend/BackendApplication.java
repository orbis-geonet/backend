package to.orbis.v2.backend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import to.orbis.v2.backend.utils.MemoryUtils;

import javax.annotation.PostConstruct;

@EnableScheduling
@SpringBootApplication
@EnableReactiveMongoRepositories
@EnableConfigurationProperties
@ConfigurationPropertiesScan
@Slf4j
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @PostConstruct
    private void memStats() {
        MemoryUtils.printCurrentMemoryInfo("Application start");
    }
}
