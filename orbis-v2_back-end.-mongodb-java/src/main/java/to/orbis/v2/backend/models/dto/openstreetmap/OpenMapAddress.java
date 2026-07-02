package to.orbis.v2.backend.models.dto.openstreetmap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMapAddress {

    String suburb;

    @JsonProperty("city_district")
    String cityDistrict;

    String city;

    String municipality;

    String county;

    @JsonProperty("state_district")
    String stateDistrict;

    String state;

    String region;

    String country;

    @JsonProperty("country_code")
    String countryCode;
}
