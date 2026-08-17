package to.orbis.v2.polygons.creator.configuration.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@ConstructorBinding
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.polygon-calculation")
public class PolygonCreatorConfiguration {
    @NotNull private Boolean enable;

    @NotNull private Boolean testControllerEnable;

    @NotNull @Min(1) private Integer checkInPointThreadNumber;

    @NotNull @Min(1) private Integer triggerPointThreadNumber;

    @NotNull @Min(1) private Integer delayInSec;

    @NotNull @Min(0) private Integer startPage;

    @NotNull @Min(1) private Integer pageSize;
}
