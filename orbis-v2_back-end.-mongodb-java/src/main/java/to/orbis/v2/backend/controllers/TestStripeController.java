package to.orbis.v2.backend.controllers;

import com.stripe.model.Charge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.configuration.StripeConfiguration;
import to.orbis.v2.backend.models.Currency;
import to.orbis.v2.backend.models.PartnerStatisticType;
import to.orbis.v2.backend.models.dto.partner.*;
import to.orbis.v2.backend.models.entity.CountResult;
import to.orbis.v2.backend.models.entity.GroupStripeAccountInfo;
import to.orbis.v2.backend.models.entity.PartnerStripeAccountInfo;
import to.orbis.v2.backend.repositories.GroupsAggregationsRepository;
import to.orbis.v2.backend.repositories.PartnerAggregationsRepository;
import to.orbis.v2.backend.repositories.SubscriptionAggregationRepository;
import to.orbis.v2.backend.services.PartnerService;
import to.orbis.v2.backend.services.PartnerStripeService;
import to.orbis.v2.backend.services.StripePaymentService;
import to.orbis.v2.backend.services.StripeWebhookService;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/stripe/test")
@ConditionalOnProperty(prefix = "stripe", name = "test-mode-enable", havingValue = "true")
public class TestStripeController {
    private final StripePaymentService stripePaymentService;
    private final StripeConfiguration stripeConfiguration;
    private final StripeWebhookService stripeWebhookService;
    private final StripeWebhookController stripeWebhookController;
    private final SubscriptionAggregationRepository repository;

    private final PartnerService partnerService;

    @GetMapping
    @PreAuthorize("permitAll")
    public Mono<CountResult> getTest(
            @RequestParam Currency currency,
            @RequestParam String userKey
    ) {
        return repository.countSubscriptionWithOtherCurrency(currency, userKey);
    }

    @PostMapping
    @PreAuthorize("permitAll")
    public Mono<Void> updatePaymentFromStripe(
            @RequestParam String stripeChargeId,
            @RequestParam String stripePaymentIntentId,
            @RequestParam Long amount
    ) {
        log.info("updatePaymentFromStripe: stripeChargeId: {}, stripePaymentIntentId: {}, amount: {}", stripeChargeId, stripePaymentIntentId, amount);
        Charge charge = new Charge();
        charge.setAmount(amount);
        charge.setId(stripeChargeId);
        charge.setPaymentIntent(stripePaymentIntentId);
        return stripePaymentService.updatePaymentFromStripe(charge);
    }

    @PostMapping("/webhook")
    @PreAuthorize("permitAll")
    public Mono<Void> commonWebhook(
            @RequestBody String payload,
            ServerWebExchange exchange
    ) {
        return stripeWebhookController.getStripeHeader(exchange)
                .flatMap(header -> stripeWebhookService.getStripeEvent(payload, header, stripeConfiguration.getStripePaymentWebhookSecret())
                        .flatMap(event -> {
                            switch (event.getType()) {
                                case "account.updated":
                                    return stripeWebhookController.getConnectInformation(payload, exchange);
                                case "charge.succeeded":
                                case "payment_intent.canceled":
                                    return stripeWebhookController.getPaymentInformation(payload, exchange);
                                case "customer.subscription.updated":
                                case "customer.subscription.deleted":
                                    return stripeWebhookController.getSubscriptionInformation(payload, exchange);
                                default:
                                    return stripeWebhookService.getStripeObject(event)
                                            .flatMap(stripeObject -> {
                                                log.info("commonWebhook: type {}", event.getType());
                                                return Mono.just(stripeObject)
                                                        .then();
                                            });
                            }
                        }));
    }


    @GetMapping("/statistic/users")
    public Mono<PartnerFullStatisticDto> getUsersStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            @RequestParam String userKey
    ) {
        log.info("getUsersStatistic: type={} from={} till={} user={}", type, from, till, userKey);
        return partnerService.getUsersStatistic(type, from, till, userKey);
    }

    @GetMapping("/statistic/groups")
    public Mono<PartnerFullStatisticDto> getGroupsStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            @RequestParam String userKey
    ) {
        log.info("getUsersStatistic: type={} from={} till={} user={}", type, from, till, userKey);
        return partnerService.getGroupsStatistic(type, from, till, userKey);
    }

    @GetMapping("/statistic/groups/info")
    public Mono<List<PartnerStatisticGroupInfoDto>> getGroupsStatisticInfo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            @RequestParam String userKey
    ) {
        log.info("getGroupsStatisticInfo: from={} till={} user={}", from, till, userKey);
        return partnerService.getGroupsStatisticInfo(from, till, userKey);
    }

    @GetMapping("/statistic/subscription")
    public Mono<PartnerFullStatisticDto> getSubscriptionStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            @RequestParam String userKey
    ) {
        log.info("getSubscriptionStatistic: type={} from={} till={} user={}", type, from, till, userKey);
        return partnerService.getSubscriptionStatistic(type, from, till, userKey);
    }

    @GetMapping("/statistic/subscription/info")
    public Mono<List<PartnerStatisticSubscriptionInfoDto>> getSubscriptionStatisticInfo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            @RequestParam String userKey
    ) {
        log.info("getSubscriptionStatisticInfo: from={} till={} user={}", from, till, userKey);
        return partnerService.getSubscriptionStatisticInfo(from, till, userKey);
    }

    @GetMapping("/statistic/group-performance")
    public Mono<PartnerAmountFullStatisticDto> getGroupPerformanceStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            @RequestParam String userKey
    ) {
        log.info("getGroupPerformanceStatistic: type={} from={} till={} user={}", type, from, till, userKey);
        return partnerService.getGroupPerformanceStatistic(type, from, till, userKey);
    }

    @GetMapping("/statistic/group-performance/info")
    public Mono<List<PartnerStatisticGroupPerformanceInfoDto>> getGroupPerformanceStatisticInfo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            @RequestParam String userKey
    ) {
        log.info("getSubscriptionStatisticInfo: from={} till={} user={}", from, till, userKey);
        return partnerService.getGroupPerformanceStatisticInfo(from, till, userKey);
    }

    @GetMapping("/statistic/earning")
    public Mono<PartnerAmountFullStatisticDto> getPartnerEarningStatistic(
            @RequestParam PartnerStatisticType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            @RequestParam String userKey
    ) {
        log.info("getPartnerEarningStatistic: type={} from={} till={} user={}", type, from, till, userKey);
        return partnerService.getPartnerEarningStatistic(type, from, till, userKey);
    }

    @GetMapping("/statistic/earning/info")
    public Mono<List<PartnerStatisticEarningInfoDto>> getPartnerEarningStatisticInfo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate till,
            @RequestParam String userKey
    ) {
        log.info("getPartnerEarningStatisticInfo: from={} till={} user={}", from, till, userKey);
        return partnerService.getPartnerEarningStatisticInfo(from, till, userKey);
    }
}
