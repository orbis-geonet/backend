package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.PartnerStatus;
import to.orbis.v2.backend.models.dto.partner.CreatePartnerResponseDto;
import to.orbis.v2.backend.models.dto.partner.PartnerFullDto;
import to.orbis.v2.backend.models.dto.stripe.CreateAccountResponseDto;
import to.orbis.v2.backend.models.dto.stripe.StripeAccountInfoDto;
import to.orbis.v2.backend.models.entity.Partner;
import to.orbis.v2.backend.models.entity.User;

@Mapper(componentModel = "spring",
        disableSubMappingMethodsGeneration = true,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PartnerMapper {

    @Mapping(target = "partnerKey", source = "partner.partnerKey")
    @Mapping(target = "stripeAccountKey", source = "stripeAccount.stripeAccountKey")
    @Mapping(target = "setupAccountUrl", source = "stripeAccount.setupAccountUrl")
    @Mapping(target = "status", source = "partner.status")
    CreatePartnerResponseDto toCreatePartnerResponseDto(Partner partner, CreateAccountResponseDto stripeAccount);

    @Mapping(target = "partnerKey", source = "partner.partnerKey")
    @Mapping(target = "status", source = "partner.status")
    @Mapping(target = "partnerLink", expression = "java(getPartnerLink(partner))")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "displayName", source = "user.displayName")
    @Mapping(target = "stripeInfo", source = "stripeInfo")
    PartnerFullDto toPartnerFullDto(Partner partner, User user, StripeAccountInfoDto stripeInfo);

    default String getPartnerLink(Partner partner) {
        return partner.getStatus().equals(PartnerStatus.READY) ?
                partner.getPartnerLink() :
                null;
    }
}
