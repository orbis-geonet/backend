package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.User;
import to.orbis.v2.backend.repositories.PartnerRepository;

import static to.orbis.v2.backend.models.PartnerStatus.READY;

@Service
@RequiredArgsConstructor
public class PartnerStripeService {
    private final PartnerRepository partnerRepository;
    private final UsersService usersService;

    public Mono<Void> setPartnerActivated(String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .flatMap(partner -> {
                    partner.setStatus(READY);
                    return partnerRepository.save(partner);
                })
                .then();
    }

    public Mono<User> getUserByPartnerKey(String partnerKey) {
        return partnerRepository.findByPartnerKey(partnerKey)
                .flatMap(partner -> usersService.findUser(partner.getUserKey(), "Cannot find"));
    }
}
