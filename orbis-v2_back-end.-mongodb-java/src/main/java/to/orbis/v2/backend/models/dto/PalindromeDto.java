package to.orbis.v2.backend.models.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;

@Data
@Validated
@Builder
public class PalindromeDto {
    public List<List<PointDto>> circles;
    public Set<CircleDto> circlesDimension;
    public List<List<PointDto>> lines;
    public Set<TangentPoints> tangentPoints;
    public Set<PalindromeLineDto> palindromeLines;
}
