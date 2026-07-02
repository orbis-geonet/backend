package to.orbis.v2.backend.models.dto.branch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateLinkRequestDto {
    @JsonProperty("branch_key")
    String branchKey;

    String channel;

    String feature;

    String campaign;

    String stage;

    List<String> tags;

    CreateDataLinkRequestDto data;
}
