package to.orbis.v2.backend.models.requests.places;

import lombok.Data;
import org.springframework.validation.annotation.Validated;


import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Validated
public class RatePlaceRequest {
    @NotNull
    String placeKey;

    @NotNull
    @Min(0)
    @Max(5)
    Double rate;
}
