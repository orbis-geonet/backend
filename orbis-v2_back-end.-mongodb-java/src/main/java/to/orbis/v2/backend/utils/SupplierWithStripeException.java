package to.orbis.v2.backend.utils;

import com.stripe.exception.StripeException;

@FunctionalInterface
public interface SupplierWithStripeException<T> {
    T getWithStripeException() throws StripeException;
}
