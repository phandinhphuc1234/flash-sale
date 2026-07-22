package com.philia.flashsale.product.adapter.out.persistence.admin;

import java.math.BigDecimal;
import java.util.UUID;

import com.philia.flashsale.product.domain.model.VariantStatus;
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
