package to.orbis.v2.backend.utils;

import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.util.regex.Pattern;

@NoArgsConstructor
public class SlugUtils {

    public static String createEmptySlug(String name) {
        if (name == null) {
            return null;
        }

        String nfdNormalizedString = Normalizer.normalize(name, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("")
                .replaceAll("[^a-zA-Z0-9]", "")
                .replace(" ", "").toLowerCase();
    }

    public static String getSlugNames(String slug, String key, long count) {
        if (slug == null || slug.isEmpty()) {
            return key;
        } else if (count > 0) {
            return slug + "-" + count;
        } else {
            return slug;
        }
    }
}
