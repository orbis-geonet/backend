package to.orbis.v2.backend.models.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Builder
public class PalindromeLineWithPointsDto {
    public List<PointDto> lineFirst;
    public List<PointDto> circleFirst;
    public List<PointDto> lineSecond;
    public List<PointDto> circleSecond;
}
