package to.orbis.v2.backend.repositories.queries;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import to.orbis.v2.backend.models.PostType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Data
public class CommentQuery {
    Optional<String> postKey;
    Optional<String> commentKey;

    Optional<Long> skip;
    Optional<Integer> limit;

    public static Builder builder() {
        return new Builder();
    }

    @FieldDefaults(makeFinal = false, level = AccessLevel.PRIVATE)
    public static class Builder {
        Optional<String> postKey = Optional.empty();
        Optional<String> commentKey = Optional.empty();

        Optional<Long> skip = Optional.empty();
        Optional<Integer> limit = Optional.empty();

        public CommentQuery build() {
            return new CommentQuery(postKey, commentKey, skip, limit);
        }

        public Builder withPostKey(String postKey) {
            this.postKey = Optional.of(postKey);
            return this;
        }

        public Builder withCommentKey(String commentKey) {
            this.commentKey = Optional.of(commentKey);
            return this;
        }

        public Builder withPageable(Pageable pageable) {
            this.skip = Optional.of(pageable.getOffset());
            this.limit = Optional.of(pageable.getPageSize());
            return this;
        }
    }

}
