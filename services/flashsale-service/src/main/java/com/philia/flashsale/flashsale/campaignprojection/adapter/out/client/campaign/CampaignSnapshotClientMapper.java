package com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign;

import com.philia.flashsale.flashsale.campaignprojection.application.exception.CampaignSnapshotRecoveryException;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignProjectionState;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Maps and validates the external snapshot from Campaign Service before it
 * reaches the projection domain.
 */
public final class CampaignSnapshotClientMapper {

    public CampaignSaleProjection toProjection(CampaignSnapshotClientResponse response) {
        if (response == null || response.campaignId() == null || response.item() == null
                || response.startAt() == null || response.endAt() == null
                || response.status() == null || response.aggregateVersion() <= 0) {
            throw invalidResponse();
        }
        CampaignProjectionState state;
        try {
            state = CampaignProjectionState.valueOf(response.status().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidResponse();
        }
        if (state == CampaignProjectionState.RECOVERY_REQUIRED) {
            throw invalidResponse();
        }
        CampaignSnapshotClientResponse.CampaignSnapshotItemResponse item = response.item();
        try {
            return CampaignSaleProjection.recovered(
                    response.campaignId(), response.aggregateVersion(), state,
                    response.startAt(), response.endAt(),
                    new CampaignItemProjection(
                            Objects.requireNonNull(item.variantId()),
                            Objects.requireNonNull(item.inventoryAllocationId()),
                            Objects.requireNonNull(item.variantSku()),
                            Objects.requireNonNull(item.campaignPrice()),
                            Objects.requireNonNull(item.currency()),
                            item.allocatedQuantity(), item.purchaseLimitPerUser()),
                    Instant.now());
        } catch (RuntimeException exception) {
            if (exception instanceof CampaignSnapshotRecoveryException recoveryException) {
                throw recoveryException;
            }
            throw invalidResponse();
        }
    }

    private CampaignSnapshotRecoveryException invalidResponse() {
        return new CampaignSnapshotRecoveryException(
                CampaignSnapshotRecoveryException.Failure.INVALID_RESPONSE,
                Duration.ofSeconds(5));
    }
}
