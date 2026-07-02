package to.orbis.v2.backend.models.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.parameters.P;
import to.orbis.v2.backend.models.PostComponentType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class PostComponent {

    public static PostComponent post(ExtendedPost post) {
        return new PostComponent(PostComponentType.POST, post, null);
    }

    public static PostComponent slider(List<ExtendedPost> checkins) {
        return new PostComponent(PostComponentType.SLIDER, null, Collections.unmodifiableList(checkins));
    }

    PostComponentType type;
    ExtendedPost post;
    List<ExtendedPost> slider;
}
