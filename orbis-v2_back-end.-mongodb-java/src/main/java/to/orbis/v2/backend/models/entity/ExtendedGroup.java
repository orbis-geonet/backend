package to.orbis.v2.backend.models.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import to.orbis.v2.backend.models.dto.stripe.StripeAccountInfoDto;

@EqualsAndHashCode(callSuper = true)
@Data
@FieldNameConstants(asEnum = true)
public class ExtendedGroup extends SimplifiedGroup {
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
