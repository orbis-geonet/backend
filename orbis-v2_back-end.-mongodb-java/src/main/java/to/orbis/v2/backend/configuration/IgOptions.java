package to.orbis.v2.backend.configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "instagram")
@RequiredArgsConstructor
@Getter
@ConstructorBinding
public class IgOptions {
    String clientId;
    String redirectUri;
    String secret;
}
