package to.orbis.v2.backend.models.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.util.Objects;

@Data
@Validated
@Builder
@AllArgsConstructor
public class PointDto {
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @NotNull
    Double longitude;
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @NotNull
    Double latitude;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PointDto pointDto = (PointDto) obj;
        return Double.compare(pointDto.longitude, longitude) == 0 &&
                Double.compare(pointDto.latitude, latitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(longitude, latitude);
    }
}
