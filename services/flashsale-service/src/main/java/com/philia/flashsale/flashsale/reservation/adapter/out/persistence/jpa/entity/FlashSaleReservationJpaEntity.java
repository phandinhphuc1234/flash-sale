package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
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
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/** Durable reservation snapshot used by later order/query flows. */
@Entity
@Table(name = "flash_sale_reservations")
public class FlashSaleReservationJpaEntity {
    @Id private UUID id;
    @Column(name = "purchase_request_id", nullable = false, unique = true) private UUID purchaseRequestId;
    @Column(name = "campaign_id", nullable = false) private UUID campaignId;
    @Column(name = "variant_id", nullable = false) private UUID variantId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "inventory_allocation_id", nullable = false) private UUID inventoryAllocationId;
    @Column(name = "sku_snapshot", nullable = false, length = 120) private String skuSnapshot;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal unitPrice;
    @JdbcTypeCode(Types.CHAR) @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false) private long quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FlashSaleReservationJpaEntity() { }

    public static FlashSaleReservationJpaEntity reserved(AcceptedReservationSnapshot snapshot) {
        var entity = new FlashSaleReservationJpaEntity();
        entity.id = snapshot.reservationId();
        entity.purchaseRequestId = snapshot.purchaseRequestId();
        entity.campaignId = snapshot.campaignId();
        entity.variantId = snapshot.variantId();
        entity.userId = snapshot.userId();
        entity.inventoryAllocationId = snapshot.inventoryAllocationId();
        entity.skuSnapshot = snapshot.skuSnapshot();
        entity.unitPrice = snapshot.unitPrice();
        entity.currency = snapshot.currency();
        entity.quantity = snapshot.quantity();
        entity.status = Status.RESERVED;
        entity.expiresAt = snapshot.expiresAt();
        entity.createdAt = snapshot.acceptedAt();
        entity.updatedAt = snapshot.acceptedAt();
        return entity;
    }

    public boolean expire(Instant at) {
        if (status != Status.RESERVED || at.isBefore(expiresAt)) return false;
        status = Status.EXPIRED;
        updatedAt = at;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getPurchaseRequestId() { return purchaseRequestId; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getVariantId() { return variantId; }
    public UUID getUserId() { return userId; }
    public UUID getInventoryAllocationId() { return inventoryAllocationId; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrency() { return currency; }
    public long getQuantity() { return quantity; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public enum Status { RESERVED, EXPIRED }
}
