package to.orbis.v2.backend.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AggregationUtils {

    public static final String ADD_FIELDS = "$addFields";
    public static final String LOOK_UP = "$lookup";


    public static String getCollectionName(Class<?> clazz, boolean sAtTheEnd) {
        return Character.toLowerCase(clazz.getSimpleName().charAt(0)) + clazz.getSimpleName().substring(1) +
                (sAtTheEnd ? "s": "");
    }

    public static String makeMatchName(String unwindName, String fieldName) {
        return String.format("%s.%s", unwindName, fieldName);
    }

    public static String makeValueName(String unwindName, String fieldName) {
        return String.format("$%s.%s", unwindName, fieldName);
    }
}
