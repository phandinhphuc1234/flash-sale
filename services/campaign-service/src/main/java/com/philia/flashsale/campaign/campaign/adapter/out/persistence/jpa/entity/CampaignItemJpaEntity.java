package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA representation of the single configured Campaign variant. */
@Entity(name = "CampaignItemJpaEntity")
@Table(name = "campaign_items")
public class CampaignItemJpaEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false, unique = true)
    private CampaignJpaEntity campaign;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "inventory_allocation_id")
    private UUID inventoryAllocationId;

    @Column(name = "variant_sku_snapshot", length = 100)
    private String variantSkuSnapshot;

    @Column(name = "base_price_snapshot", precision = 19, scale = 4)
    private BigDecimal basePriceSnapshot;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_snapshot", columnDefinition = "char(3)")
    private String currencySnapshot;

    @Column(name = "campaign_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal campaignPrice;

    @Column(name = "requested_quantity", nullable = false)
    private long requestedQuantity;

    @Column(name = "allocated_quantity", nullable = false)
    private long allocatedQuantity;

    @Column(name = "purchase_limit_per_user", nullable = false)
    private long purchaseLimitPerUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA; application code should use the persistence mapper. */
    public CampaignItemJpaEntity() {
    }

    public void attachCampaign(CampaignJpaEntity campaign) {
        this.campaign = campaign;
    }

    public void detachCampaign() {
        this.campaign = null;
    }

    @PrePersist
    void initializeCreateTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CampaignJpaEntity getCampaign() {
        return campaign;
    }

    public void setCampaign(CampaignJpaEntity campaign) {
        this.campaign = campaign;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public UUID getInventoryAllocationId() {
        return inventoryAllocationId;
    }

    public void setInventoryAllocationId(UUID inventoryAllocationId) {
        this.inventoryAllocationId = inventoryAllocationId;
    }

    public String getVariantSkuSnapshot() {
        return variantSkuSnapshot;
    }

    public void setVariantSkuSnapshot(String variantSkuSnapshot) {
        this.variantSkuSnapshot = variantSkuSnapshot;
    }

    public BigDecimal getBasePriceSnapshot() {
        return basePriceSnapshot;
    }

    public void setBasePriceSnapshot(BigDecimal basePriceSnapshot) {
        this.basePriceSnapshot = basePriceSnapshot;
    }

    public String getCurrencySnapshot() {
        return currencySnapshot;
    }

    public void setCurrencySnapshot(String currencySnapshot) {
        this.currencySnapshot = currencySnapshot;
    }

    public BigDecimal getCampaignPrice() {
        return campaignPrice;
    }

    public void setCampaignPrice(BigDecimal campaignPrice) {
        this.campaignPrice = campaignPrice;
    }

    public long getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(long requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public long getAllocatedQuantity() {
        return allocatedQuantity;
    }

    public void setAllocatedQuantity(long allocatedQuantity) {
        this.allocatedQuantity = allocatedQuantity;
    }

    public long getPurchaseLimitPerUser() {
        return purchaseLimitPerUser;
    }

    public void setPurchaseLimitPerUser(long purchaseLimitPerUser) {
        this.purchaseLimitPerUser = purchaseLimitPerUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
