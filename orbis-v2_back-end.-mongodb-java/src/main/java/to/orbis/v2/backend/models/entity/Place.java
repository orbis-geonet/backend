package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;
import to.orbis.v2.backend.models.PlaceType;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "places")
@FieldNameConstants(asEnum = true)
@Slf4j
public class Place extends Entity {
    GeoJsonPoint coordinates;
    String name;
    String placeKey;
    PlaceType type;
    String userCreatedKey;
    String source;
    String address;
    String description;
    String categoryKey;
    String cityKey;
    String countryKey;
    String csvHash;
    String csvUrl;
    String phone;
    List<WorkingHours> workingHours;
    String website;
    Double totalRate;
    Integer countRates;
    Instant lastCheckInTimestamp;
    Instant lastSizeChangeTimestamp;
    String dominantGroupKey;
    Boolean deleted;
    Instant creationServerTimestamp;
    Instant timestamp;
    Instant createTimestamp;
    String groupCreatedKey;
    String googlePlaceId;
    Double lastSize;
    String imageName;
    boolean reported;
    String reportedMessage;
    Boolean reportedSolved;
    Instant reportedTime;
    String shareLink;
    String fullShareLink;
    PlaceAddress googleAddress;
    String slug;
    String emptySlug;
    String checkInPolygonCoordinateKey;

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

    public Place checkin() {
        lastSize = Math.max(500, Math.min(currentSize() + 100, 1000.0));
        lastCheckInTimestamp = Instant.now();
        lastSizeChangeTimestamp = lastCheckInTimestamp;
        return this;
    }

    public double touched(int checkins) {
        int checkinsLeft = checkins;
        double currentSize = this.currentSize();

        double res = 0.0;

        // this is with resist
        // alternatively >= 600 clause can be removed and on any touch size of the touched can be reset to 500
        if (currentSize >= 600) {
            if (currentSize - checkinsLeft * 100 >= 500) {
                res = currentSize - checkinsLeft * 100;
                // other way to achieve no resistance is to remove following statement
                // and then grade based checkin will kick in and do the job
                // albeit when touched by a bigger circle first time can lead to unexpected results
                //if(resistance.checked) {
                checkinsLeft = 0;
                //}
            } else {
                checkinsLeft -= Math.floor((currentSize - 500) / 100);
                currentSize = 500;
            }
        } else if (currentSize >= 500) {
            currentSize = 500;
        }

        if (checkinsLeft > 0) {
            int currentGrade = findGrade(currentSize);
            int updatedGrade = currentGrade + checkinsLeft;
            if (updatedGrade <= 0) {
                log.warn("Updated grade was less then or equal to 0 for placeKey: {}, previousSize: {}, checkins: {}", placeKey, currentSize, checkinsLeft);
                res = 500;
            } else if (updatedGrade < grades.length) {
                res = grades[updatedGrade];
            } else {
                res = 0;
            }
        }

        return res;
    }

    private static double[] grades = new double[]{500, 250, 150, 100, 50, 50, 25, 25, 17, 9, 4.5, 2.25};

    private static int findGrade(double currentSize) {
        for (int i = 0; i < grades.length; i++) {
            double g = grades[i];
            if (g < currentSize) {
                return i - 1;
            }
        }

        return 20;
    }
}
