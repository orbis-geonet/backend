package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.dto.*;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.models.requests.users.UserDetailsRequest;
import to.orbis.v2.backend.models.requests.users.UserSignupRequest;

@Mapper(componentModel = "spring", uses = {PointMapper.class},
        disableSubMappingMethodsGeneration = true,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UserMapper {
    @Mapping(target = "seen", ignore = true)
    UserDto userToUserDto(User user);

    // here because simplified group is only used in extended user
    SimplifiedGroupDto simplifiedGroupToSimplifiedGroupDto(SimplifiedGroup extendedGroup);

    @Mapping(target = "idToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "seen", ignore = true)
    ExtendedUserDto extendedUserToExtendedUserDto(ExtendedUser user);

    PrimitiveUserDto primitiveUserToPrimitiveUserDto(PrimitiveUser primitiveUser);

    @Mapping(target = "following", ignore = true)
    @Mapping(target = "totalFollowing", ignore = true)
    @Mapping(target = "followedGroups", ignore = true)
    @Mapping(target = "followedPlaces", ignore = true)
    @Mapping(target = "totalFollowers", ignore = true)
    @Mapping(target = "groupAdminCount", ignore = true)
    @Mapping(target = "groupMemberCount", ignore = true)
    @Mapping(target = "pending", ignore = true)
    @Mapping(target = "blocked", ignore = true)
    ExtendedUser userToExtendedUser(User user);

    @Mapping(target = "coordinates", ignore = true)
    @Mapping(target = "superAdmin", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "activeServerTimestamp", ignore = true)
    @Mapping(target = "idToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "seen", ignore = true)
    ExtendedUserDto extendedUserToExtendedUserDtoSkipSensitiveFields(ExtendedUser user);

    @Mapping(target = "userKey", ignore = true)
    @Mapping(target = "superAdmin", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    @Mapping(target = "activeServerTimestamp", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fcmTokens", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    @Mapping(target = "reported", ignore = true)
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "blockedBy", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "customerStripeId", ignore = true)
    User userDetailsRequestToToUser(UserDetailsRequest userDetailsRequest);

    @Mapping(target = "userKey", ignore = true)
    @Mapping(target = "superAdmin", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    @Mapping(target = "activeServerTimestamp", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fcmTokens", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    @Mapping(target = "reported", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "blockedBy", ignore = true)
    @Mapping(target = "customerStripeId", ignore = true)
    User userSignupRequestToUser(UserSignupRequest userSignupRequest);

    ChatUserDto chatUserToChatUserDto(ChatUser chatUser);

    @Mapping(target = "seen", ignore = true)
    SimplifiedUser userToSimplifiedUser(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fcmTokens", ignore = true)
    @Mapping(target = "blockedBy", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "customerStripeId", ignore = true)
    User extendedUserToUser(ExtendedUser user);
}
