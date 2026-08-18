package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.projection.OwnedPaymentProjection;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.OwnedPaymentQueryJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentAttemptJpaRepository;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentByOrderQuery;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentQuery;
import com.philia.flashsale.payment.payment.application.model.query.PaymentDetailsResult;
import com.philia.flashsale.payment.payment.application.port.out.LoadOwnedPaymentPort;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter for safe owner Payment projections and attempt counts. */
public class OwnedPaymentQueryJpaAdapter implements LoadOwnedPaymentPort {
    private final OwnedPaymentQueryJpaRepository payments;
    private final PaymentAttemptJpaRepository attempts;

    public OwnedPaymentQueryJpaAdapter(OwnedPaymentQueryJpaRepository payments,
            PaymentAttemptJpaRepository attempts) {
        this.payments = Objects.requireNonNull(payments, "payments");
        this.attempts = Objects.requireNonNull(attempts, "attempts");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentDetailsResult> load(GetOwnedPaymentQuery query) {
        Objects.requireNonNull(query, "query");
        return payments.findByIdAndUserId(query.paymentId(), query.ownerId()).map(this::toResult);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentDetailsResult> loadByOrder(GetOwnedPaymentByOrderQuery query) {
        Objects.requireNonNull(query, "query");
        return payments.findByOrderIdAndUserId(query.orderId(), query.ownerId()).map(this::toResult);
    }

    private PaymentDetailsResult toResult(OwnedPaymentProjection payment) {
        return new PaymentDetailsResult(payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getPaymentDeadline(),
                Math.toIntExact(attempts.countByPayment_Id(payment.getId())), payment.getFailureReason(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
