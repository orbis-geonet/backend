package to.orbis.v2.backend.configuration;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.Writer;

@Configuration
public class MustacheConfiguration {

    @Bean
    public MustacheFactory mustacheFactory() {

        return new DefaultMustacheFactory() {
            @Override
            @SneakyThrows
            public void encode(String value, Writer writer) {
                writer.write(value);
            }
        };
    }
}
