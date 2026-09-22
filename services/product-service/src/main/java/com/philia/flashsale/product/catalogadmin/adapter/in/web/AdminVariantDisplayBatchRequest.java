package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

record AdminVariantDisplayBatchRequest(
        @NotEmpty(message = "variantIds must not be empty")
        @Size(max = 100, message = "variantIds must contain at most 100 IDs")
        List<@NotNull @Valid UUID> variantIds) {
}
