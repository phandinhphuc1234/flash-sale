package com.philia.flashsale.product.catalog.adapter.in.web.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Internal Cart-to-Product request; only variant identities cross this boundary. */
public record VariantDisplayBatchRequest(
        @NotNull List<@Valid @NotNull UUID> variantIds) { }
