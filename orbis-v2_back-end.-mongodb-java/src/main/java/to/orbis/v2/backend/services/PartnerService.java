package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.PartnerException;
import to.orbis.v2.backend.mappers.PartnerMapper;
import to.orbis.v2.backend.mappers.StripeMapper;
import to.orbis.v2.backend.models.PartnerStatisticType;
import to.orbis.v2.backend.models.dto.partner.*;
import to.orbis.v2.backend.models.dto.stripe.CreateAccountDto;
import to.orbis.v2.backend.models.entity.Partner;
import to.orbis.v2.backend.models.entity.PartnerStripeAccountInfo;
import to.orbis.v2.backend.repositories.PartnerAggregationsRepository;
import to.orbis.v2.backend.repositories.PartnerRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartnerService {
    private final StripeAccountService stripeAccountService;
    private final UsersService usersService;
    private final PartnerRepository partnerRepository;
    private final PartnerAggregationsRepository partnerAggregationsRepository;
    private final StripeMapper stripeMapper;
    private final PartnerMapper partnerMapper;
    private final BranchService branchService;

    @Transactional
    public Mono<CreatePartnerResponseDto> becomePartner(
            CreateAccountDto createAccountDto,
            String userKey
    ) {
        return partnerRepository.findByUserKey(userKey)
                .flatMap(partner -> updatePartnerStripeAccount(userKey))
                .switchIfEmpty(
                        createNewPartner(userKey)
                                .flatMap(partner -> stripeAccountService.createAccount(stripeMapper.createAccountDtoToStripeAccount(createAccountDto, userKey))
                                        .flatMap(stripeLink -> Mono.just(partnerMapper.toCreatePartnerResponseDto(partner, stripeLink))))
                );

    }

    public Mono<CreatePartnerResponseDto> updatePartnerStripeAccount(String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .switchIfEmpty(Mono.error(() -> new PartnerException("There is no partner for user: " + userKey)))
                .flatMap(partner -> stripeAccountService.updateAccount(partner.getUserKey())
                        .flatMap(stripeLink -> Mono.just(partnerMapper.toCreatePartnerResponseDto(partner, stripeLink)))
                );
    }

    public Mono<PartnerFullDto> getInformation(String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .switchIfEmpty(Mono.error(() -> new PartnerException("There is no partner for user: " + userKey)))
                .flatMap(partner -> usersService.findUser(partner.getUserKey(), "Cannot find user")
                        .flatMap(user -> stripeAccountService.getAccountInto(user.getUserKey())
                                .flatMap(stripeInfo -> Mono.just(partnerMapper.toPartnerFullDto(partner, user, stripeInfo)))
                        )
                );
    }

    public Mono<PartnerStripeAccountInfo> getPartnerStripeInfo(String partnerKey) {
        return partnerAggregationsRepository.findPartnerStripeInfo(partnerKey);
    }

    private Mono<Partner> createNewPartner(String userKey) {
        var partner = new Partner(userKey);
        return branchService.getLink(partner.getPartnerKey())
                .flatMap(link -> {
                    partner.setPartnerLink(link);
                    return partnerRepository.save(partner);
                });
    }

    public Mono<PartnerFullStatisticDto> getUsersStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .flatMap(partner -> partnerAggregationsRepository.countNewUsers(partner.getPartnerKey())
                        .flatMap(totalCount -> partnerAggregationsRepository.getUsersStatistic(type, from, plusDay(till), partner.getPartnerKey())
                                .flatMap(statistic -> Mono.just(
                                        PartnerFullStatisticDto.builder()
                                                .resultList(statistic)
                                                .totalNumber(totalCount.getResult().longValue())
                                                .build()
                                ))
                        )
                );
    }

    public Mono<PartnerFullStatisticDto> getGroupsStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .flatMap(partner -> partnerAggregationsRepository.countGroups(partner.getPartnerKey())
                        .flatMap(totalCount -> partnerAggregationsRepository.getGroupsStatistic(type, from, plusDay(till), partner.getPartnerKey())
                                .flatMap(statistic -> Mono.just(
                                        PartnerFullStatisticDto.builder()
                                                .resultList(statistic)
                                                .totalNumber(totalCount.getResult().longValue())
                                                .build()
                                ))
                        )
                );
    }

    public Mono<List<PartnerStatisticGroupInfoDto>> getGroupsStatisticInfo(LocalDate from, LocalDate till, String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .flatMap(partner -> partnerAggregationsRepository.getGroupsStatisticInfo(from, plusDay(till), partner.getPartnerKey()));
    }

    public Mono<PartnerFullStatisticDto> getSubscriptionStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .flatMap(partner -> partnerAggregationsRepository.countSubscription(partner.getPartnerKey())
                        .flatMap(totalCount -> partnerAggregationsRepository.getSubscriptionStatistic(type, from, plusDay(till), partner.getPartnerKey())
                                .flatMap(statistic -> Mono.just(
                                        PartnerFullStatisticDto.builder()
                                                .resultList(statistic)
                                                .totalNumber(totalCount.getResult().longValue())
                                                .build()
                                ))
                        )
                );
    }

    public Mono<List<PartnerStatisticSubscriptionInfoDto>> getSubscriptionStatisticInfo(LocalDate from, LocalDate till, String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .flatMap(partner -> partnerAggregationsRepository.getSubscriptionStatisticInfo(from, plusDay(till), partner.getPartnerKey()));
    }

    public Mono<PartnerAmountFullStatisticDto> getGroupPerformanceStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .flatMap(partner -> partnerAggregationsRepository.countGroupPerformance(partner.getPartnerKey())
                        .flatMap(totalCount -> partnerAggregationsRepository.getGroupPerformanceStatistic(type, from, plusDay(till), partner.getPartnerKey())
                                .flatMap(statistic -> Mono.just(
                                        PartnerAmountFullStatisticDto.builder()
                                                .resultList(statistic)
                                                .totalAmount(totalCount.getResult())
                                                .build()
                                ))
                        )
                );
    }

    public Mono<List<PartnerStatisticGroupPerformanceInfoDto>> getGroupPerformanceStatisticInfo(LocalDate from, LocalDate till, String userKey) {
        return partnerRepository.findByUserKey(userKey)
                .flatMap(partner -> partnerAggregationsRepository.getGroupPerformanceStatisticInfo(from, plusDay(till), partner.getPartnerKey()));
    }

    public Mono<PartnerAmountFullStatisticDto> getPartnerEarningStatistic(PartnerStatisticType type, LocalDate from, LocalDate till, String userKey) {
        return partnerAggregationsRepository.countPartnerEarning(userKey)
                        .flatMap(totalCount -> partnerAggregationsRepository.getPartnerEarningStatistic(type, from, plusDay(till), userKey)
                                .flatMap(statistic -> Mono.just(
                                        PartnerAmountFullStatisticDto.builder()
                                                .resultList(statistic)
                                                .totalAmount(totalCount.getResult())
                                                .build()
                                ))
                );
    }

    public Mono<List<PartnerStatisticEarningInfoDto>> getPartnerEarningStatisticInfo(LocalDate from, LocalDate till, String userKey) {
        return partnerAggregationsRepository.getPartnerEarningStatisticInfo(from, plusDay(till), userKey);
    }

    private LocalDate plusDay(LocalDate date) {
        return date.plusDays(1);
    }
}
