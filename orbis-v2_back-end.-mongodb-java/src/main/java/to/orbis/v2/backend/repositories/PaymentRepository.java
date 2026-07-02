package to.orbis.v2.backend.repositories;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.entity.Payment;

public interface PaymentRepository extends ReactiveMongoRepository<Payment, ObjectId> {
    Mono<Payment> findByPaymentIntentStripeId(String paymentIntentStripeId);

    Mono<Payment> findByInvoiceStripeId(String invoiceStripeId);
}
