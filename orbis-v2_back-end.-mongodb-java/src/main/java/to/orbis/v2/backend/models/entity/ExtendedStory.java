package to.orbis.v2.backend.models.entity;

import lombok.Data;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;

@Data
public class ExtendedStory {
    ObjectId _id;
    SimplifiedGroup group;
    List<ExtendedPost> posts;
    Instant timestamp;
    Double dist;
}
