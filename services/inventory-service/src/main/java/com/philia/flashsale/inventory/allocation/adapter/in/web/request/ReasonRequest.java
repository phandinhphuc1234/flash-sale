package com.philia.flashsale.inventory.allocation.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;

/** HTTP request carrying the audit reason for releasing an allocation. */
public record ReasonRequest(@NotBlank String reason) {
}
