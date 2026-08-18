package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.projection;

import com.philia.flashsale.payment.payment.domain.model.FailureReason;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Closed projection that intentionally excludes provider and attempt internals. */
public interface OwnedPaymentProjection {
    UUID getId();
    UUID getOrderId();
    BigDecimal getAmount();
    String getCurrency();
    PaymentStatus getStatus();
    Instant getPaymentDeadline();
    FailureReason getFailureReason();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
