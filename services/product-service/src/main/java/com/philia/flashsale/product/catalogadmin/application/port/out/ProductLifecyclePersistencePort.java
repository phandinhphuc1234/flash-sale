package com.philia.flashsale.product.catalogadmin.application.port.out;

import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;

public interface ProductLifecyclePersistencePort {
    long updateLifecycle(UUID productId, long expectedVersion, ProductStatus target, Instant publishedAt);
}
