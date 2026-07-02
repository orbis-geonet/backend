package to.orbis.v2.backend.configuration.web;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import to.orbis.v2.backend.models.Language;

@Component
public class StringToLanguageConverter implements Converter<String, Language> {
    @Override
    public Language convert(String source) {
        try {
            return Language.valueOf(source.toUpperCase());
        } catch (Exception e) {
            return Language.EN;
        }
    }
}
