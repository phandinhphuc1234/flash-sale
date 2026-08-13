package com.philia.flashsale.flashsale.reservation.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** Public JSON request; caller identity and price are never accepted from this body. */
public record ReserveCampaignQuotaRequest(
        @NotNull UUID variantId,
        @Positive long quantity) {
}
