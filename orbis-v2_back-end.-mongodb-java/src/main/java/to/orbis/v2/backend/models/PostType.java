package to.orbis.v2.backend.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import to.orbis.v2.backend.exceptions.EnumValidationException;
import to.orbis.v2.backend.models.entity.ExtendedPost;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum PostType {
     TEXT,
     IMAGE,
     AUDIO,
     CHECK_IN,
     EVENT,
     VIDEO;

    @JsonCreator
    public static PostType create(String value) throws EnumValidationException {
        if (value == null)
            return null;

        try {
            return PostType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new EnumValidationException("Valid values for post type are: "+
                    Arrays.stream(PostType.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    public boolean isMedia() {
        return this == IMAGE || this == VIDEO;
    }
}
