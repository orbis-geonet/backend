package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.dto.*;
import to.orbis.v2.backend.models.entity.*;

import java.util.List;

@Mapper(componentModel = "spring",
        disableSubMappingMethodsGeneration = true,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SubscriptionMapper {

    SubscriptionDto subscriptionToSubscriptionDto(Subscription subscription);

    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "createTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "deleted", expression = "java(false)")
    @Mapping(target = "createdUserKey", source = "createdUserKey")
    @Mapping(target = "groupKey", source = "groupKey")
    Subscription subscriptionCreateDtoToSubscription(SubscriptionCreateDto subscriptionDto, String groupKey, String createdUserKey);

    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "createTimestamp", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdUserKey", source = "userKey")
    @Mapping(target = "groupKey", source = "groupKey")
    Subscription subscriptionUpdateDtoToSubscription(SubscriptionUpdateDto subscription, String groupKey, String userKey);

    @Mapping(target = "name", ignore = true)
    @Mapping(target = "groupName", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "benefit", ignore = true)
    UserSubscriptionDto subscriptionUserToUserSubscriptionDto(UserSubscription userSubscription);

    default UserPurchaseGroupDto toUserPurchaseGroupDto(UserPurchaseDto dto) {
        return UserPurchaseGroupDto.builder()
                .name(dto.getName())
                .purchaseKey(dto.getPurchaseKey())
                .group(
                        UserPurchaseGroupDto.GroupPurchaseDto.builder()
                                .groupKey(dto.getGroupKey())
                                .groupName(dto.getGroupName())
                                .build()
                )
                .user(
                        UserPurchaseGroupDto.UserPurchaseDto.builder()
                                .displayName(dto.getDisplayName())
                                .userKey(dto.getUserKey())
                                .build()
                )
                .price(dto.getPrice())
                .currency(dto.getCurrency())
                .number(dto.getNumber())
                .codes(dto.getCodes())
                .build();
    }
}
