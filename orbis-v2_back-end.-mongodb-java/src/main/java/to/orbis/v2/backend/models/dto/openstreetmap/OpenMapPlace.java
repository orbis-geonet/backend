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
public class OpenMapPlace {

    @JsonProperty("place_id")
    String placeId;

    String licence;

    @JsonProperty("osm_type")
    String osmType;

    @JsonProperty("osm_id")
    String osm_id;

    String lat;

    String lon;

    @JsonProperty("place_rank")
    Integer placeRank;

    String category;

    String type;

    @JsonProperty("addresstype")
    String addressType;

    String name;

    @JsonProperty("display_name")
    String displayName;

    OpenMapAddress address;
}
