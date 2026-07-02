package to.orbis.v2.backend.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@Slf4j
public class FirebaseConfiguration {

    @PostConstruct
    @SneakyThrows
    public void initFirebase() {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                //.setDatabaseUrl("https://orbis-v2.firebaseio.com/")
                .build();

        FirebaseApp.initializeApp(options);
        log.info("Firebase initialized");
    }
}
