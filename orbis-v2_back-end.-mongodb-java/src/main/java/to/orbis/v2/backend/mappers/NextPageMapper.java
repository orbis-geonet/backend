package to.orbis.v2.backend.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.stereotype.Component;
import to.orbis.v2.backend.models.entity.NextPage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class NextPageMapper {

    ObjectMapper objectMapper;

    @SneakyThrows
    public String nextPageToB64String(NextPage nextPage) {
        val json = objectMapper.writeValueAsString(nextPage);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    public NextPage b64StringToNextPage(String nextPage) {
        val json = Base64.getDecoder().decode(nextPage);
        return objectMapper.readValue(json, NextPage.class);
    }
}
