package com.philia.flashsale.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.philia.flashsale.product.domain.model.ProductAggregate;
import com.philia.flashsale.product.domain.model.ProductStatus;

class ProductAggregateDraftTests {

    @Test
    void createDraftStartsAsNonPublicDraft() {
        ProductAggregate draft = ProductAggregate.createDraft(
                null,
                "PROD-001",
                "product-001",
                "Product 001",
                "Short",
                "Long");

        assertThat(draft.id()).isNotNull();
        assertThat(draft.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(draft.publishedAt()).isNull();
        assertThat(draft.version()).isZero();
        assertThat(draft.hiddenFromShopperCatalogWithoutVariants()).isTrue();
    }

    @Test
    void createDraftRejectsBlankRequiredIdentifiers() {
        assertThatThrownBy(() -> ProductAggregate.createDraft(
                null,
                " ",
                "product-001",
                "Product 001",
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product code is required");
    }
}
