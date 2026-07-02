package to.orbis.v2.backend.repositories.queries;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import to.orbis.v2.backend.models.PostType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Data
@AllArgsConstructor
public class PostQuery {

    Optional<String> postKey;
    Optional<String> userKey;
    Optional<String> placeKey;
    Optional<String> groupKey;
    Optional<String> city;
    Optional<List<PostType>> postTypes;

    Optional<String> viewerUserKey;

    Optional<Instant> moment;
    boolean pastEvents;

    Optional<Instant> after;

    Optional<Integer> page;
    Optional<Integer> limit;
    Optional<Instant> from;

    Optional<GeoJsonPoint> point;
    double distance;
    boolean inverseGeoQuery;
    boolean dedup;

    public static Builder builder() {
        return new Builder();
    }

    @FieldDefaults(makeFinal = false, level = AccessLevel.PRIVATE)
    public static class Builder {
        Optional<String> postKey = Optional.empty();
        Optional<String> userKey = Optional.empty();
        Optional<String> placeKey = Optional.empty();
        Optional<String> groupKey = Optional.empty();
        Optional<String> city = Optional.empty();
        Optional<List<PostType>> postTypes = Optional.empty();

        Optional<String> viewerUserKey = Optional.empty();

        Optional<Instant> moment = Optional.empty();
        boolean pastEvents = false;

        Optional<Instant> after = Optional.empty();

        Optional<Integer> page = Optional.empty();
        Optional<Integer> limit = Optional.empty();
        Optional<Instant> from = Optional.empty();
        boolean dedup = false;

        Optional<GeoJsonPoint> point = Optional.empty();
        double distance = 0.0;
        boolean inverseGeoQuery = false;

        public Builder postKey(String postKey) {
            this.postKey = Optional.ofNullable(postKey);
            return this;
        }

        public Builder viewerUserKey(Optional<String> viewerUserKey) {
            this.viewerUserKey = viewerUserKey;
            return this;
        }

        public Builder viewerUserKey(String viewerUserKey) {
            this.viewerUserKey = Optional.ofNullable(viewerUserKey);
            return this;
        }

        public Builder userKey(String userKey) {
            this.userKey = Optional.ofNullable(userKey);
            return this;
        }

        public Builder placeKey(String placeKey) {
            this.placeKey = Optional.ofNullable(placeKey);
            return this;
        }

        public Builder groupKey(String groupKey) {
            this.groupKey = Optional.ofNullable(groupKey);
            return this;
        }

        public Builder city(String city) {
            this.city = Optional.ofNullable(city);
            return this;
        }

        public PostQuery build() {
            return new PostQuery(postKey, userKey, placeKey, groupKey, city, postTypes, viewerUserKey, moment, pastEvents, after, page, limit, from, point, distance, inverseGeoQuery, dedup);
        }

        public Builder from(Optional<Instant> from) {
            this.from = from;
            return this;
        }

        public Builder dedup(boolean dedup) {
            this.dedup = dedup;
            return this;
        }

        public Builder near(GeoJsonPoint point, double distance) {
            this.point = Optional.ofNullable(point);
            this.distance = distance;
            return this;
        }

        public Builder limit(int limitPerGroup) {
            this.limit = Optional.of(limitPerGroup);
            return this;
        }

        public Builder postTypes(List<PostType> postTypes) {
            this.postTypes = Optional.ofNullable(postTypes);
            return this;
        }

        public Builder page(int page) {
            this.page = Optional.of(page);
            return this;
        }

        public Builder eventStarts(Instant moment) {
            this.moment = Optional.ofNullable(moment);
            return this;
        }

        public Builder pastEvents(boolean pastEvents) {
            this.pastEvents = pastEvents;
            return this;
        }

        public Builder further(GeoJsonPoint point, double distance) {
            this.point = Optional.of(point);
            this.distance = distance;
            this.inverseGeoQuery = true;
            return this;
        }

        public Builder after(Instant after) {
            this.after = Optional.of(after);
            return this;
        }
    }
}
