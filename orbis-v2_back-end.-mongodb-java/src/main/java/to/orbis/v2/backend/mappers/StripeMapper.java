package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.dto.stripe.CreateAccountDto;
import to.orbis.v2.backend.models.dto.stripe.StripeAccountInfoDto;
import to.orbis.v2.backend.models.entity.StripeAccount;

@Mapper(componentModel = "spring",
        disableSubMappingMethodsGeneration = true,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StripeMapper {

    @Mapping(target = "userKey", source = "userKey")
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "createTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "deleted", expression = "java(false)")
    StripeAccount createAccountDtoToStripeAccount(CreateAccountDto createAccountDto, String userKey);


    StripeAccountInfoDto stripeAccountToStripeAccountInfoDto(StripeAccount stripeAccount);
}
