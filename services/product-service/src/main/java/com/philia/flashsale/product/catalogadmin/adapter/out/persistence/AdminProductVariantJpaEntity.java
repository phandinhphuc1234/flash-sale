package com.philia.flashsale.product.catalogadmin.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.VariantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "AdminProductVariantJpaEntity")
@Table(name = "product_variants")
class AdminProductVariantJpaEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String sku;

    private String barcode;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VariantStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected AdminProductVariantJpaEntity() {
    }

    static AdminProductVariantJpaEntity from(UUID productId,
            com.philia.flashsale.product.catalogadmin.application.command.MaintainProductCompositionCommand.VariantInput input) {
        AdminProductVariantJpaEntity entity = new AdminProductVariantJpaEntity();
        entity.id = input.id() == null ? UUID.randomUUID() : input.id();
        entity.productId = productId;
        entity.update(input);
        return entity;
    }

    void update(com.philia.flashsale.product.catalogadmin.application.command.MaintainProductCompositionCommand.VariantInput input) {
        this.sku = input.sku().trim();
        this.barcode = input.barcode() == null || input.barcode().isBlank() ? null : input.barcode().trim();
        this.name = input.name().trim();
        this.basePrice = input.basePrice();
        this.currency = input.currency();
        this.status = input.status();
        this.sortOrder = input.sortOrder();
    }

    void deactivate() { this.status = VariantStatus.INACTIVE; }

    UUID id() {
        return id;
    }

    String sku() {
        return sku;
    }

    String barcode() {
        return barcode;
    }

    String name() {
        return name;
    }

    BigDecimal basePrice() {
        return basePrice;
    }

    String currency() {
        return currency;
    }

    VariantStatus status() {
        return status;
    }

    int sortOrder() {
        return sortOrder;
    }
}
