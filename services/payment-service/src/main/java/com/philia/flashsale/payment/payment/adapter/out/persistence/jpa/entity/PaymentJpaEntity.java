package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.payment.payment.domain.model.FailureReason;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA representation of the Payment aggregate; never exposed to the application core. */
@Entity
@Table(name = "payments")
public class PaymentJpaEntity {

    @Id
    private UUID id;
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "payment_deadline", nullable = false)
    private Instant paymentDeadline;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 64)
    private FailureReason failureReason;
    @Column(name = "succeeded_at")
    private Instant succeededAt;
    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentAttemptJpaEntity> attempts = new ArrayList<>();

    protected PaymentJpaEntity() {
    }

    public static PaymentJpaEntity from(Payment payment) {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.updateFrom(payment);
        entity.id = payment.id();
        entity.createdAt = payment.createdAt();
        entity.rowVersion = 0;
        entity.attempts = new ArrayList<>();
        for (var attempt : payment.attempts()) {
            entity.attempts.add(PaymentAttemptJpaEntity.from(attempt, entity));
        }
        return entity;
    }

    public void updateFrom(Payment payment) {
        this.id = payment.id();
        this.orderId = payment.orderId();
        this.userId = payment.userId();
        this.amount = payment.amount();
        this.currency = payment.currency();
        this.paymentDeadline = payment.paymentDeadline();
        this.status = payment.status();
        this.failureReason = payment.failureReason();
        this.succeededAt = payment.succeededAt();
        this.aggregateVersion = payment.aggregateVersion();
        this.updatedAt = payment.updatedAt();
        if (this.createdAt == null) {
            this.createdAt = payment.createdAt();
        }
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getPaymentDeadline() { return paymentDeadline; }
    public PaymentStatus getStatus() { return status; }
    public FailureReason getFailureReason() { return failureReason; }
    public Instant getSucceededAt() { return succeededAt; }
    public long getAggregateVersion() { return aggregateVersion; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<PaymentAttemptJpaEntity> getAttempts() { return attempts; }
}
