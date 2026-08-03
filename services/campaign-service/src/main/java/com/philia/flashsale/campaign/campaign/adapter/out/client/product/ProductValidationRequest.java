package com.philia.flashsale.campaign.campaign.adapter.out.client.product;

import java.util.UUID;

/** Product Service wire request; it never crosses the outbound adapter boundary. */
public record ProductValidationRequest(UUID variantId) {
}
