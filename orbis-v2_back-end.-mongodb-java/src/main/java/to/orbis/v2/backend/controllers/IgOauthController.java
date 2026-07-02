package to.orbis.v2.backend.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.services.IgService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/igoauth")
@Slf4j
public class IgOauthController {

    IgService service;
    ObjectMapper objectMapper;

    /*
    callback?code=<some_code>&state=<some_state>#_
    to be exchanged for access_token and later to long-term access token
     */
    @GetMapping("/callback")
    public Mono<String> callback(@RequestParam String code, @RequestParam String state) {
        return service.finishConnect(code, state).map(unused -> "Connected");
    }

    @PostMapping(path = "/deauthorize")
    public Mono<Void> deauthorize(ServerWebExchange exchange) {

        val formData = exchange.getMultipartData();

        return formData
                .flatMapMany(data -> {
                    log.info("Data: {}", String.join(", ", data.keySet()));
                    val request = data.get("signed_request").get(0);

                    return request.content();
                })
                .map(pc -> {
                    return pc.toString(StandardCharsets.UTF_8);
                })
                .buffer()
                .singleOrEmpty()
                .map(l -> String.join("", l))
                .map(partContent -> partContent.split("\\.")[1])
                .flatMap(baseEncoded -> Mono.fromCallable(() -> getParsedData(baseEncoded)))
                .flatMap(params -> {
                    String userId = (String) params.get("user_id");
                    return service.removeIgLinkForIgUserId(Long.valueOf(userId));
                });
    }

    @SneakyThrows
    private Map<String, Object> getParsedData(String data) {
        return objectMapper.readValue(Base64.getDecoder().decode(data), new TypeReference<Map<String, Object>>() {
        });
    }

    @PostMapping(value = "/deletedata", consumes = "application/x-www-form-urlencoded", produces = "application/json")
    public Mono<String> deleteData(@RequestParam Map<String, String> reqParam, @RequestBody String body) {

        log.info("Delete data called with: {} and body: {}", reqParam.entrySet().stream()
                .map(e -> String.format("%s -> %s", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n")), body);

        return Mono.just("removed");
    }

}
