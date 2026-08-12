package com.philia.flashsale.flashsale.campaignprojection.domain.model;

import com.philia.flashsale.flashsale.campaignprojection.domain.exception.InvalidCampaignProjectionException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Campaign projection aggregate protecting window, version, and sellability invariants. */
public final class CampaignSaleProjection {
    private final UUID campaignId;
    private final long aggregateVersion;
    private final CampaignProjectionState state;
    private final Instant startsAt;
    private final Instant endsAt;
    private final CampaignItemProjection item;
    private final Instant updatedAt;

    private CampaignSaleProjection(UUID campaignId, long aggregateVersion, CampaignProjectionState state,
            Instant startsAt, Instant endsAt, CampaignItemProjection item, Instant updatedAt) {
        this.campaignId = Objects.requireNonNull(campaignId, "campaignId");
        this.aggregateVersion = requirePositiveVersion(aggregateVersion);
        this.state = Objects.requireNonNull(state, "state");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
        this.item = Objects.requireNonNull(item, "item");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!startsAt.isBefore(endsAt)) {
            throw new InvalidCampaignProjectionException("Campaign start must be before its end");
        }
        if (state == CampaignProjectionState.RECOVERY_REQUIRED) {
            throw new InvalidCampaignProjectionException("Recovery marker is not a complete snapshot");
        }
    }

    public static CampaignSaleProjection scheduled(UUID campaignId, long aggregateVersion,
            Instant startsAt, Instant endsAt, CampaignItemProjection item, Instant updatedAt) {
        return new CampaignSaleProjection(campaignId, aggregateVersion, CampaignProjectionState.SCHEDULED,
                startsAt, endsAt, item, updatedAt);
    }

    public static CampaignSaleProjection recovered(UUID campaignId, long aggregateVersion,
            CampaignProjectionState state, Instant startsAt, Instant endsAt,
            CampaignItemProjection item, Instant updatedAt) {
        if (state == CampaignProjectionState.RECOVERY_REQUIRED) {
            throw new InvalidCampaignProjectionException("Recovery snapshot must have a lifecycle state");
        }
        return new CampaignSaleProjection(campaignId, aggregateVersion, state,
                startsAt, endsAt, item, updatedAt);
    }

    public UUID campaignId() {
        return campaignId;
    }

    public long aggregateVersion() {
        return aggregateVersion;
    }

    public CampaignProjectionState state() {
        return state;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public CampaignItemProjection item() {
        return item;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isNewerThan(long version) {
        return aggregateVersion > version;
    }

    public boolean isSellableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return state == CampaignProjectionState.ACTIVE
                && !now.isBefore(startsAt)
                && now.isBefore(endsAt);
    }

    public boolean hasSameWindow(Instant start, Instant end) {
        return startsAt.equals(start) && endsAt.equals(end);
    }

    private static long requirePositiveVersion(long version) {
        if (version <= 0) {
            throw new InvalidCampaignProjectionException("Campaign aggregate version must be positive");
        }
        return version;
    }
}
