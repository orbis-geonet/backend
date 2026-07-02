package to.orbis.v2.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.BranchConfiguration;
import to.orbis.v2.backend.models.dto.branch.CreateDataLinkRequestDto;
import to.orbis.v2.backend.models.dto.branch.CreateLinkRequestDto;
import to.orbis.v2.backend.models.dto.branch.CreateLinkResponseDto;

@Service
@Slf4j
public class BranchService {
    BranchConfiguration branchConfiguration;
    WebClient webClient;

    public BranchService(BranchConfiguration branchConfiguration, WebClient.Builder builder) {
        this.branchConfiguration = branchConfiguration;
        this.webClient = builder
                .build();
    }

    public Mono<String> getLink(String partnerKey) {
        var body = CreateLinkRequestDto.builder()
                .branchKey(branchConfiguration.getKey())
                .data(CreateDataLinkRequestDto.builder().partnerKey(partnerKey).build())
                .build();
        return webClient.post()
                .uri(UriComponentsBuilder.fromHttpUrl(branchConfiguration.getCreateLinkUrl()).build().toUri())
                .bodyValue(body)
                .retrieve().bodyToMono(CreateLinkResponseDto.class)
                .map(CreateLinkResponseDto::getUrl)
                .retry(5);
    }
}
