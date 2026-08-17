package to.orbis.v2.polygons.creator.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

@EqualsAndHashCode
@Data
@Document(collection = "places")
@FieldNameConstants(asEnum = true)
@Slf4j
public class Place {
    ObjectId id;
    GeoJsonPoint coordinates;
    String name;
    String placeKey;
    Instant lastCheckInTimestamp;
    Instant lastSizeChangeTimestamp;
    String dominantGroupKey;
    Instant timestamp;
    Instant createTimestamp;
    Instant creationServerTimestamp;
    String groupCreatedKey;
    Double lastSize;

    public Instant getLastSizeChangeTimestamp() {
        if (this.lastSizeChangeTimestamp != null)
            return this.lastSizeChangeTimestamp;

        return this.lastCheckInTimestamp;
    }

    private static final long DAY = 24 * 60 * 60 * 1000;
    private static final long YEAR = 365 * DAY;

    public double currentSize() {
        var placeSize = lastSize;
        if (placeSize == null) placeSize = 500.0;

        var lastKnownTime = firstNonNull(
                lastSizeChangeTimestamp,
                lastCheckInTimestamp,
                creationServerTimestamp,
                timestamp,
                Instant.parse("2021-01-01T00:00:00Z")
        );

        val elapsedTime = (double) Duration.between(
                lastKnownTime,
                Instant.now()
        ).toMillis();

        if (elapsedTime < DAY && placeSize >= 500) {
            placeSize = (placeSize - 500) * ((DAY - elapsedTime) / DAY) + 500;
        } else if (placeSize >= 500) {
            placeSize = 500 * (YEAR - elapsedTime) / YEAR;
        } else {
            placeSize = placeSize * (YEAR - elapsedTime) / YEAR;
        }

        if (placeSize < 0) placeSize = 0.0;

        return placeSize;
    }

    private Instant firstNonNull(Instant... timestamps) {
        //noinspection OptionalGetWithoutIsPresent last one is always non-null
        return Arrays.stream(timestamps).filter(Objects::nonNull).findFirst().get();
    }

    private static double[] grades = new double[]{500, 250, 150, 100, 50, 50, 25, 25, 17, 9, 4.5, 2.25};

}
