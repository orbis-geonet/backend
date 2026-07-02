package to.orbis.v2.backend.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import to.orbis.v2.backend.exceptions.EnumValidationException;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum PlaceType {
    SHOPPING,
    PARK,
    BAR,
    RESTAURANT,
    SCHOOL,
    LOCATION,
    TWO_BUILDINGS,
    SPORTS_CENTER,
    CASTLE,
    HOUSE_2,
    MUSIC,
    FAST_FOOD,
    HOUSE,
    BUILDING,
    BEACH;

    @JsonCreator
    public static PlaceType create(String value) throws EnumValidationException {
        if (value == null)
            return null;

        try {
            return PlaceType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new EnumValidationException("Valid values for place type are: "+
                    Arrays.stream(PlaceType.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }
}
