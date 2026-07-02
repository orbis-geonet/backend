package to.orbis.v2.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.PartnerStatisticType;
import to.orbis.v2.backend.models.StatisticSubscriptionType;
import to.orbis.v2.backend.models.dto.partner.*;
import to.orbis.v2.backend.models.dto.stripe.CreateAccountDto;
import to.orbis.v2.backend.services.PartnerService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/partner")
@RequiredArgsConstructor
public class PartnerController {
    private final PartnerService partnerService;

    @PostMapping
    public Mono<CreatePartnerResponseDto> becomePartner(
            @RequestBody CreateAccountDto createAccountDto,
            Authentication authentication
    ) {
        log.info("becomePartner: createAccountDto={} userKey={}", createAccountDto, authentication.getName());
        return partnerService.becomePartner(createAccountDto, authentication.getName());
    }

    @PatchMapping
    public Mono<CreatePartnerResponseDto> updatePartnerStripeAccount(Authentication authentication) {
        log.info("updatePartnerStripeAccount: userKey={}", authentication.getName());
        return partnerService.updatePartnerStripeAccount(authentication.getName());
    }

    @GetMapping
    public Mono<PartnerFullDto> getInformation(Authentication authentication) {
        log.info("getInformation: userKey={}", authentication.getName());
        return partnerService.getInformation(authentication.getName());
    }

    @GetMapping("/statistic/users")
    public Mono<PartnerFullStatisticDto> getUsersStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            Authentication authentication
    ) {
        log.info("getUsersStatistic: type={} from={} till={} user={}", type, from, till, authentication.getName());
        return partnerService.getUsersStatistic(type, from, till, authentication.getName());
    }

    @GetMapping("/statistic/groups")
    public Mono<PartnerFullStatisticDto> getGroupsStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            Authentication authentication
    ) {
        log.info("getUsersStatistic: type={} from={} till={} user={}", type, from, till, authentication.getName());
        return partnerService.getGroupsStatistic(type, from, till, authentication.getName());
    }

    @GetMapping("/statistic/groups/info")
    public Mono<List<PartnerStatisticGroupInfoDto>> getGroupsStatisticInfo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            Authentication authentication
    ) {
        log.info("getGroupsStatisticInfo: from={} till={} user={}", from, till, authentication.getName());
        return partnerService.getGroupsStatisticInfo(from, till, authentication.getName());
    }

    @GetMapping("/statistic/subscription")
    public Mono<PartnerFullStatisticDto> getSubscriptionStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            Authentication authentication
    ) {
        log.info("getSubscriptionStatistic: type={} from={} till={} user={}", type, from, till, authentication.getName());
        return partnerService.getSubscriptionStatistic(type, from, till, authentication.getName());
    }

    @GetMapping("/statistic/subscription/info")
    public Mono<List<PartnerStatisticSubscriptionInfoDto>> getSubscriptionStatisticInfo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            Authentication authentication
    ) {
        log.info("getSubscriptionStatisticInfo: from={} till={} user={}", from, till, authentication.getName());
        return partnerService.getSubscriptionStatisticInfo(from, till, authentication.getName());
    }

    @GetMapping("/statistic/group-performance")
    public Mono<PartnerAmountFullStatisticDto> getGroupPerformanceStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            Authentication authentication
    ) {
        log.info("getSubscriptionStatistic: type={} from={} till={} user={}", type, from, till, authentication.getName());
        return partnerService.getGroupPerformanceStatistic(type, from, till, authentication.getName());
    }

    @GetMapping("/statistic/group-performance/info")
    public Mono<List<PartnerStatisticGroupPerformanceInfoDto>> getGroupPerformanceStatisticInfo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            Authentication authentication
    ) {
        log.info("getSubscriptionStatisticInfo: from={} till={} user={}", from, till, authentication.getName());
        return partnerService.getGroupPerformanceStatisticInfo(from, till, authentication.getName());
    }

    @GetMapping("/statistic/earning")
    public Mono<PartnerAmountFullStatisticDto> getPartnerEarningStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            Authentication authentication
    ) {
        log.info("getPartnerEarningStatistic: type={} from={} till={} user={}", type, from, till, authentication.getName());
        return partnerService.getPartnerEarningStatistic(type, from, till, authentication.getName());
    }

    @GetMapping("/statistic/earning/info")
    public Mono<List<PartnerStatisticEarningInfoDto>> getPartnerEarningStatisticInfo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            Authentication authentication
    ) {
        log.info("getPartnerEarningStatisticInfo: from={} till={} user={}", from, till, authentication.getName());
        return partnerService.getPartnerEarningStatisticInfo(from, till, authentication.getName());
    }

}
