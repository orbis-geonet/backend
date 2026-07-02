package to.orbis.v2.backend.configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import javax.validation.constraints.NotNull;

@Getter
@ConstructorBinding
@ConfigurationProperties(prefix = "branch")
@RequiredArgsConstructor
public class BranchConfiguration {
    @NotNull
    String key;

    @NotNull
    String createLinkUrl;
}
