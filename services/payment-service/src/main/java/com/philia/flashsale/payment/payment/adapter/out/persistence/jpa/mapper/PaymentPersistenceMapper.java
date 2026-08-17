package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.mapper;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentAttemptJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import com.philia.flashsale.payment.payment.domain.model.Money;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttempt;
import java.util.List;

/** Explicit adapter-local mapper between the aggregate and its JPA representation. */
public class PaymentPersistenceMapper {

    public PaymentJpaEntity toEntity(Payment payment) {
        return PaymentJpaEntity.from(payment);
    }

    public Payment toDomain(PaymentJpaEntity entity) {
        List<PaymentAttempt> attempts = entity.getAttempts().stream()
                .map(this::toDomain)
                .toList();
        return Payment.reconstitute(entity.getId(), entity.getOrderId(), entity.getUserId(),
                Money.of(entity.getAmount(), entity.getCurrency()), entity.getPaymentDeadline(),
                entity.getStatus(), entity.getFailureReason(), entity.getSucceededAt(),
                entity.getAggregateVersion(), entity.getCreatedAt(), entity.getUpdatedAt(), attempts);
    }

    public PaymentAttempt toDomain(PaymentAttemptJpaEntity entity) {
        return PaymentAttempt.reconstitute(entity.getId(), entity.getPayment().getId(),
                entity.getAttemptNumber(), entity.getProvider(), entity.getProviderIdempotencyKey(),
                entity.getStatus(), entity.getProviderSessionId(), entity.getProviderPaymentIntentId(),
                entity.getFirstSubmittedAt(), entity.getSafeReplayUntil(), entity.getProviderExpiresAt(),
                entity.getLastProviderState(), entity.getFailureReason(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
