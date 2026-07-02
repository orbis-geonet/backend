package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.exceptions.NoDataFoundException;
import to.orbis.v2.backend.exceptions.StripeException;
import to.orbis.v2.backend.exceptions.SubscriptionException;
import to.orbis.v2.backend.mappers.StripeMapper;
import to.orbis.v2.backend.models.StripeAccountStatus;
import to.orbis.v2.backend.models.dto.stripe.CreateAccountResponseDto;
import to.orbis.v2.backend.models.dto.stripe.StripeAccountInfoDto;
import to.orbis.v2.backend.models.entity.StripeAccount;
import to.orbis.v2.backend.repositories.StripeAccountRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StripeAccountService {
    private final StripeService stripeService;
    private final PartnerStripeService partnerStripeService;
    private final StripeAccountRepository stripeAccountRepository;
    private final GroupsService groupsService;
    private final StripeMapper stripeMapper;

    public Mono<CreateAccountResponseDto> createAccount(StripeAccount stripeAccountNew) {
        return stripeAccountRepository.findByUserKeyAndDeletedFalse(stripeAccountNew.getUserKey())
                .flatMap(stripeAccount -> {
                    if (stripeAccount.getStatus() == StripeAccountStatus.READY_TO_USE) {
                        return Mono.error(() -> new StripeException("User has already had an activated stripe account. You cannot create a new one."));
                    }
                    return Mono.just(stripeAccount);
                })
                .switchIfEmpty(
                        stripeService.createAccount(stripeAccountNew.getCountry(), stripeAccountNew.getBusinessType())
                                .flatMap(stripeId -> {
                                    var id = new ObjectId();
                                    stripeAccountNew.setStripeAccountKey(id.toHexString());
                                    stripeAccountNew.setStripeId(stripeId);
                                    stripeAccountNew.setId(id);
                                    stripeAccountNew.setStatus(StripeAccountStatus.CREATED);
                                    return stripeAccountRepository.save(stripeAccountNew);
                                })
                )
                .flatMap(account -> stripeService.createAccountLinkOnboarding(account.getStripeId())
                        .flatMap(url -> createResponse(account.getStripeAccountKey(), url)));
    }

    public Mono<CreateAccountResponseDto> updateAccount(String userKey) {
        return stripeAccountRepository.findByUserKeyAndDeletedFalse(userKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Stripe account not found")))
                .flatMap(stripeAccount -> stripeService.createAccountLinkUpdate(stripeAccount.getStripeId())
                        .flatMap(url -> createResponse(stripeAccount.getStripeAccountKey(), url)));
    }

    public Mono<CreateAccountResponseDto> verificationInfoAccount(String userKey) {
        return stripeAccountRepository.findByUserKeyAndDeletedFalse(userKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Stripe account not found")))
                .flatMap(stripeAccount -> stripeService.createAccountLinkVerification(stripeAccount.getStripeId())
                        .flatMap(url -> createResponse(stripeAccount.getStripeAccountKey(), url)));
    }

    public Mono<Void> deleteAccount(String userKey) {
        return stripeAccountRepository.findByUserKeyAndDeletedFalse(userKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Stripe account not found")))
                .flatMap(stripeAccount -> groupsService.countGroupWithActivatedSubscriptionsByMainAdmin(stripeAccount.getUserKey())
                        .flatMap(count -> {
                            if (count > 0) {
                                return Mono.error(() -> new SubscriptionException("You cannot delete a stripe account, when you have a group with activated subscription"));
                            } else {
                                stripeAccount.setDeleted(true);
                                stripeAccount.setTimestamp(Instant.now());
                                return stripeAccountRepository.save(stripeAccount)
                                        .then();
                            }
                        }).then());
    }

    public Mono<String> getStripeAccountId(String userKey) {
        return stripeAccountRepository.findByUserKeyAndDeletedFalse(userKey)
                .flatMap(stripeAccount -> Mono.just(stripeAccount.getStripeAccountKey()));
    }

    public Mono<StripeAccountInfoDto> getAccountInto(String userKey) {
        return stripeAccountRepository.findByUserKeyAndDeletedFalse(userKey)
                .switchIfEmpty(Mono.error(() -> new NoDataFoundException("Stripe account didn't find")))
                .flatMap(account -> Mono.just(stripeMapper.stripeAccountToStripeAccountInfoDto(account)));
    }

    public Mono<Void> updateAccountFromWebhook(String id, Boolean payoutsEnabled, List<String> pastDue) {
        return stripeAccountRepository.findByStripeId(id)
                .switchIfEmpty(Mono.error(() -> new StripeException("There is no account with stripeId " + id)))
                .flatMap(stripeAccount -> {
                    if (Boolean.TRUE.equals(payoutsEnabled)) {
                        stripeAccount.setStatus(StripeAccountStatus.READY_TO_USE);
                        stripeAccount.setTimestamp(Instant.now());
                    } else {
                        stripeAccount.setStatus(StripeAccountStatus.VALIDATION_FAILED);
                        stripeAccount.setFieldError(pastDue);
                    }
                    return stripeAccountRepository.save(stripeAccount)
                            .flatMap(resultStripeAccount -> {
                                if (resultStripeAccount.getStatus() == StripeAccountStatus.READY_TO_USE) {
                                    return groupsService.setSubscriptionActivated(resultStripeAccount.getUserKey())
                                            .then(partnerStripeService.setPartnerActivated(resultStripeAccount.getUserKey()));
                                } else {
                                    return Mono.empty();
                                }
                            });
                });
    }

    private Mono<CreateAccountResponseDto> createResponse(String accountKey, String url) {
        return Mono.just(
                CreateAccountResponseDto.builder()
                        .stripeAccountKey(accountKey)
                        .setupAccountUrl(url)
                        .build()
        );
    }
}
