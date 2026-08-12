package com.philia.flashsale.campaign.campaign.domain.policy;

import com.philia.flashsale.campaign.campaign.domain.exception.CampaignDomainException;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.time.Instant;

/**
 * Centralizes the time and state rules for Campaign lifecycle operations.
 *
 * <p>The policy only inspects the aggregate and returns typed domain failures;
 * persistence, HTTP, scheduling, and authentication remain outside this
 * package.</p>
 */
public final class CampaignLifecyclePolicy {

    /** Requires the monotonic transition defined by the Campaign state machine. */
    public void requireAllowedTransition(CampaignStatus current, CampaignStatus target) {
        if (current == null || target == null || !current.canTransitionTo(target)) {
            throw new CampaignDomainException(
                    CampaignDomainException.INVALID_TRANSITION,
                    "Campaign transition from " + current + " to " + target + " is not allowed");
        }
    }

    /** Validates the preconditions for moving a draft into the scheduled state. */
    public void ensureCanSchedule(Campaign campaign, Instant now) {
        requireCampaign(campaign, now);
        requireStatus(campaign, CampaignStatus.DRAFT);
        if (!now.isBefore(campaign.startAt())) {
            throw new CampaignDomainException(
                    CampaignDomainException.START_TIME_IN_PAST,
                    "Campaign cannot be scheduled at or after its start time");
        }
        requireReadyItem(campaign);
    }

    /** Validates the preconditions for activating a scheduled campaign. */
    public void ensureCanActivate(Campaign campaign, Instant now) {
        requireCampaign(campaign, now);
        requireStatus(campaign, CampaignStatus.SCHEDULED);
        if (now.isBefore(campaign.startAt()) || !now.isBefore(campaign.endAt())) {
            throw new CampaignDomainException(
                    CampaignDomainException.ACTIVATION_WINDOW_INVALID,
                    "Campaign activation must be inside its sale window");
        }
        requireReadyItem(campaign);
    }

    /** Validates the preconditions for ending an active campaign. */
    public void ensureCanEnd(Campaign campaign, Instant now) {
        requireCampaign(campaign, now);
        requireStatus(campaign, CampaignStatus.ACTIVE);
        if (now.isBefore(campaign.endAt())) {
            throw new CampaignDomainException(
                    CampaignDomainException.END_TIME_NOT_REACHED,
                    "Campaign cannot end before its end time");
        }
    }

    private void requireReadyItem(Campaign campaign) {
        if (!campaign.hasItem()) {
            throw new CampaignDomainException(
                    CampaignDomainException.ITEM_REQUIRED,
                    "Campaign item is required for lifecycle progression");
        }
        if (!campaign.item().hasCompleteProductSnapshot()) {
            throw new CampaignDomainException(
                    CampaignDomainException.SNAPSHOT_INCOMPLETE,
                    "Campaign item Product snapshot is incomplete");
        }
        if (!campaign.item().hasCompleteAllocation()) {
            throw new CampaignDomainException(
                    CampaignDomainException.ALLOCATION_INCOMPLETE,
                    "Campaign item Inventory allocation is incomplete");
        }
    }

    private void requireStatus(Campaign campaign, CampaignStatus expected) {
        if (campaign.status() != expected) {
            throw new CampaignDomainException(
                    CampaignDomainException.INVALID_STATUS,
                    "Campaign must be " + expected + " but is " + campaign.status());
        }
    }

    private void requireCampaign(Campaign campaign, Instant now) {
        if (campaign == null || now == null) {
            throw new CampaignDomainException(
                    CampaignDomainException.INVALID_CAMPAIGN_DATA,
                    "Campaign and evaluation time are required");
        }
    }
}
