package to.orbis.v2.backend.mappers;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import to.orbis.v2.backend.models.dto.CompetingGroupDto;
import to.orbis.v2.backend.models.dto.ExtendedGroupDto;
import to.orbis.v2.backend.models.dto.PrimitiveGroupDto;
import to.orbis.v2.backend.models.dto.SimplifiedUserDto;
import to.orbis.v2.backend.models.entity.*;
import to.orbis.v2.backend.models.firebase.FirebaseGroup;
import to.orbis.v2.backend.models.requests.groups.CreateGroupRequest;
import to.orbis.v2.backend.models.requests.groups.UpdateGroupRequest;

@Mapper(componentModel = "spring", uses = {PointMapper.class, UserMapper.class},
        disableSubMappingMethodsGeneration = true,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface GroupMapper {

    PrimitiveGroupDto primitiveGroupToPrimitiveGroupDto(PrimitiveGroup primitiveGroup);

    @Mapping(target = "rank", ignore = true)
    @Mapping(target = "rankDiff", ignore = true)
    @Mapping(target = "isSubscriptionActivate", defaultValue = "false")
    ExtendedGroupDto extendedGroupToExtendedGroupDto(ExtendedGroup extendedGroup);

    // here because simplified user is only used in extended group
    SimplifiedUserDto simplifiedUserToSimplifiedUserDto(SimplifiedUser user);


    CompetingGroupDto competingGroupToCompetingGroupDto(CompetingGroup competingGroup);

    @Mapping(target = "adminsCount", ignore = true)
    @Mapping(target = "membersCount", ignore = true)
    @Mapping(target = "followersCount", ignore = true)
    @Mapping(target = "validCheckins", ignore = true)
    @Mapping(target = "placesCount", ignore = true)
    @Mapping(target = "isAdmin", ignore = true)
    @Mapping(target = "isMember", ignore = true)
    @Mapping(target = "isMainAdmin", ignore = true)
    @Mapping(target = "isFollower", ignore = true)
    @Mapping(target = "rank", ignore = true)
    @Mapping(target = "rankDiff", ignore = true)
    @Mapping(target = "postsThisWeek", ignore = true)
    @Mapping(target = "postsLastWeek", ignore = true)
    @Mapping(target = "isStoriesHidden", ignore = true)
    @Mapping(target = "isBlockedByUser", ignore = true)
    ExtendedGroup groupToExtendedGroup(Group group);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "groupKey", ignore = true)
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "createTimestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "admins", ignore = true)
    @Mapping(target = "mainAdmin", ignore = true)
    @Mapping(target = "followers", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "banned", ignore = true)
    @Mapping(target = "reported", ignore = true)
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "storiesHidden", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "subscriptions", ignore = true)
    @Mapping(target = "isSubscriptionActivate", ignore = true)
    @Mapping(target = "blockedBy", ignore = true)
    Group createRequestToGroup(CreateGroupRequest groupRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "groupKey", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "admins", ignore = true)
    @Mapping(target = "mainAdmin", ignore = true)
    @Mapping(target = "followers", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "banned", ignore = true)
    @Mapping(target = "reported", ignore = true)
    @Mapping(target = "shareLink", ignore = true)
    @Mapping(target = "fullShareLink", ignore = true)
    @Mapping(target = "storiesHidden", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "subscriptions", ignore = true)
    @Mapping(target = "isSubscriptionActivate", ignore = true)
    @Mapping(target = "blockedBy", ignore = true)
    Group updateRequestToGroup(UpdateGroupRequest updateRequest);

    @Mapping(target = "timestamp", expression = "java(com.google.firebase.database.ServerValue.TIMESTAMP)")
    @Mapping(target = "placeKey", ignore = true)
    @Mapping(target = "placeCoordinates", ignore = true)
    FirebaseGroup groupToFirebaseGroup(ExtendedGroup group);

    @Mapping(target = "validCheckins", constant = "0")
    @Mapping(target = "percentage", ignore = true)
    @Mapping(target = "rank", ignore = true)
    @Mapping(target = "rankDiff", ignore = true)
    @Mapping(target = "postsThisWeek", ignore = true)
    @Mapping(target = "postsLastWeek", ignore = true)
    @Mapping(target = "placesCount", ignore = true)
    CompetingGroup groupToCompetingGroup(Group group);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "admins", ignore = true)
    @Mapping(target = "followers", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "banned", ignore = true)
    @Mapping(target = "storiesHidden", ignore = true)
    @Mapping(target = "reportedMessage", ignore = true)
    @Mapping(target = "reportedSolved", ignore = true)
    @Mapping(target = "reportedTime", ignore = true)
    @Mapping(target = "blockedBy", ignore = true)
    Group extendedGroupToGroup(ExtendedGroup group);

    SimplifiedGroup groupToSimplifiedGroup(Group group);
}
