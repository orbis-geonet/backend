package to.orbis.v2.backend.repositories.queries;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
public class GroupQuery {
    Optional<String> groupKey;
    Optional<String> userKey;
    Optional<GeoJsonPoint> point;
    Optional<GeoJsonPoint> userLocation;
    Optional<Distance> distance;
    Optional<String> nameFilter;
    Optional<Pageable> pageable;
    Optional<Boolean> listMembers;
    Optional<List<String>> skipGroups;

    public static Builder group(String groupKey) {
        return builder().withGroupKey(groupKey);
    }

    public static Builder pageable(Pageable pageable) {
        return builder().withPageable(pageable);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder closeTo(GeoJsonPoint point) {
        return builder().closeTo(point);
    }

    public static Builder groupName(String name) {
        return builder().withName(name);
    }

    public interface WithPageable {
        Builder withPageable(Pageable pageable);
    }

    public interface CloseTo {
        Builder closeTo(GeoJsonPoint point);
    }

    public interface WithDistance {
        Builder withDistance(Distance distance);
    }

    public interface ForUser {
        Builder forUser(String userKey);
    }

    public interface WithName {
        Builder withName(String name);
    }

    public interface ListMembers {
        Builder listMembers(boolean listMembers);
    }

    public interface WithUserLocation {
        Builder userLocation(GeoJsonPoint userLocation);
    }

    @FieldDefaults(makeFinal = false, level = AccessLevel.PRIVATE)
    @Setter
    @With
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Builder implements WithDistance, WithName, WithPageable, ForUser, CloseTo, ListMembers, WithUserLocation {
        String groupKey;
        Pageable pageable;
        Distance distance;
        String name;
        String userKey;
        GeoJsonPoint point;
        GeoJsonPoint userLocation;
        List<String> skipGroups;
        boolean listMembers;


        @Override
        public Builder closeTo(GeoJsonPoint point) {
            return this.withPoint(point);
        }

        @Override
        public Builder forUser(String userKey) {
            return this.withUserKey(userKey);
        }

        public GroupQuery build() {
            return new GroupQuery(
                    Optional.ofNullable(groupKey),
                    Optional.ofNullable(userKey),
                    Optional.ofNullable(point),
                    Optional.ofNullable(userLocation),
                    Optional.ofNullable(distance),
                    Optional.ofNullable(name),
                    Optional.ofNullable(pageable),
                    Optional.of(listMembers),
                    Optional.ofNullable(skipGroups).filter(l -> !l.isEmpty())
            );
        }

        @Override
        public Builder listMembers(boolean listMembers) {
            this.listMembers = listMembers;
            return this;
        }

        public Builder userLocation(GeoJsonPoint userLocation) {
            this.userLocation = userLocation;
            return this;
        }
    }
}
