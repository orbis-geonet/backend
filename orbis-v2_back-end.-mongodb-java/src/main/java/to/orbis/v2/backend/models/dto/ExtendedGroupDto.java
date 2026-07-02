package to.orbis.v2.backend.models.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import to.orbis.v2.backend.models.dto.stripe.StripeAccountInfoDto;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(makeFinal = false, level = AccessLevel.PROTECTED)
public class ExtendedGroupDto extends SimplifiedGroupDto {

    Integer adminsCount;
    Integer followersCount;
    Integer membersCount;

    Boolean isAdmin;
    Boolean isMember;
    Boolean isFollower;
    Boolean isStoriesHidden;
    Boolean isBlockedByUser;

    Integer validCheckins;

    Boolean isMainAdmin;
    Boolean hasSubscription;
    Boolean hasStripeAccount;
    Boolean isSubscriptionActivate;
    Boolean isSubscriber;

    StripeAccountInfoDto mainUserStripeAccount;
}
