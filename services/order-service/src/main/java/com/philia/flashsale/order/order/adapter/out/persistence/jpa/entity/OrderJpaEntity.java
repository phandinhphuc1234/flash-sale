package com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.order.domain.model.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA representation of the Order aggregate; never exposed to the application core. */
@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    private UUID id;
    @Column(name = "order_number", nullable = false, length = 64, unique = true)
    private String orderNumber;
    @Column(name = "purchase_request_id", nullable = false, unique = true)
    private UUID purchaseRequestId;
    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;
    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAmount;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;
    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;
    @Column(name = "reservation_expires_at", nullable = false)
    private Instant reservationExpiresAt;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderJpaEntity() {
    }

    public static OrderJpaEntity from(Order order, Instant createdAt) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.id = order.id();
        entity.orderNumber = order.orderNumber();
        entity.purchaseRequestId = order.purchaseRequestId();
        entity.reservationId = order.reservationId();
        entity.campaignId = order.campaignId();
        entity.userId = order.userId();
        entity.status = order.status();
        entity.currency = order.currency();
        entity.subtotalAmount = order.subtotal().amount();
        entity.totalAmount = order.total().amount();
        entity.acceptedAt = order.acceptedAt();
        entity.reservationExpiresAt = order.reservationExpiresAt();
        entity.rowVersion = 0;
        entity.createdAt = createdAt;
        entity.updatedAt = createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public UUID getPurchaseRequestId() { return purchaseRequestId; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
