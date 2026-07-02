package to.orbis.v2.backend.configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@Getter
@Setter
@ConstructorBinding
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.email")
public class EmailSendingConfiguration {
    private String from;

    private String fromName;

    private boolean testMode;

    private String testModeReceiver;

    private String amazonConfigurationSetName;
}
