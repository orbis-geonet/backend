package to.orbis.v2.backend.models;

import com.stripe.param.AccountCreateParams;

public enum BusinessType {
    INDIVIDUAL, BUSINESS;

    public static AccountCreateParams.BusinessType getBusinessType(BusinessType type) {
        switch (type) {
            case INDIVIDUAL:
                return AccountCreateParams.BusinessType.INDIVIDUAL;
            case BUSINESS:
                return AccountCreateParams.BusinessType.COMPANY;
            default:
                throw new IllegalArgumentException("Wrong business type " + type);
        }
    }
}
