package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.mappers.IgMapper;
import to.orbis.v2.backend.models.dto.IgLinkDto;
import to.orbis.v2.backend.services.IgService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ig")
public class IgController {

    IgService igService;
    IgMapper igMapper;

    @PostMapping("/connect")
    @PreAuthorize("isAuthenticated")
    public Mono<IgLinkDto> connect(Authentication auth) {
        return igService.connect(auth.getName()).map(igMapper::igLinkToIgLinkDto);
    }
}
