package com.philia.flashsale.payment.payment.application.port.out;

import com.philia.flashsale.payment.payment.domain.model.Payment;
import java.util.Optional;
import java.util.UUID;

/** Aggregate loading capability; locking semantics are explicit at the adapter boundary. */
public interface LoadPaymentPort {

    Optional<Payment> findById(UUID paymentId);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findLockedById(UUID paymentId);
}
