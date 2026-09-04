package com.philia.flashsale.product.catalogquery.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Minimal read-only mapping required to bind the quote repository to Product's own variant table. */
@Entity
@Table(name = "product_variants")
class PurchaseQuoteJpaEntity {

    @Id
    private UUID id;

    protected PurchaseQuoteJpaEntity() {
    }
}
