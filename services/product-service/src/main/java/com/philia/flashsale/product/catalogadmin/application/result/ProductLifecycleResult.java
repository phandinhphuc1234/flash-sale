package com.philia.flashsale.product.catalogadmin.application.result;

import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;

public record ProductLifecycleResult(UUID id, ProductStatus status, Instant publishedAt, long version, boolean replayed) { }
