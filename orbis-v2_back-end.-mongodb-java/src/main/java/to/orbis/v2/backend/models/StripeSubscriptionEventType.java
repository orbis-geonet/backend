package to.orbis.v2.backend.models;


public enum StripeSubscriptionEventType {
    CREATED, UPDATED, DELETED, PAYMENT_PROBLEM;

    public static StripeSubscriptionEventType getEvent(String type) {
        switch (type) {
            case "customer.subscription.created":
                return CREATED;
            case "customer.subscription.updated":
                return UPDATED;
            case "customer.subscription.deleted":
                return DELETED;
            default:
                throw new IllegalArgumentException("Wrong business type " + type);
        }
    }
}
