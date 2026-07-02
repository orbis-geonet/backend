package to.orbis.v2.backend.models;

import java.util.Arrays;

public enum Language {
    EN,
    PT;

    public static Language get(String value) {
        return Arrays.stream(Language.values())
                .filter(it -> it.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(EN);
    }
}
