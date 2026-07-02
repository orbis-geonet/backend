package to.orbis.v2.backend.models.ig;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class IgMedia {

    String id;

    @JsonProperty("media_type")
    IgMediaType mediaType;
    @JsonProperty("media_url")
    String mediaUrl;
    @JsonProperty("thumbnail_url")
    String thumbnailUrl;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ", shape = JsonFormat.Shape.STRING)
    ZonedDateTime timestamp;
    String username;
}
