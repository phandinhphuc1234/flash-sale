package com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.order.domain.model.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA representation of the one-item Order snapshot. */
@Entity
@Table(name = "order_lines")
public class OrderLineJpaEntity {

    @Id
    private UUID id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;
    @Column(name = "variant_id", nullable = false)
    private UUID variantId;
    @Column(nullable = false)
    private long quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;
    @Column(name = "line_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineAmount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderLineJpaEntity() {
    }

    public static OrderLineJpaEntity from(OrderLine line, OrderJpaEntity order, Instant createdAt) {
        OrderLineJpaEntity entity = new OrderLineJpaEntity();
        entity.id = line.id();
        entity.order = order;
        entity.variantId = line.variantId();
        entity.quantity = line.quantity();
        entity.unitPrice = line.unitPrice().amount();
        entity.lineAmount = line.lineAmount().amount();
        entity.createdAt = createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public OrderJpaEntity getOrder() { return order; }
    public UUID getVariantId() { return variantId; }
    public long getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineAmount() { return lineAmount; }
    public Instant getCreatedAt() { return createdAt; }
}
