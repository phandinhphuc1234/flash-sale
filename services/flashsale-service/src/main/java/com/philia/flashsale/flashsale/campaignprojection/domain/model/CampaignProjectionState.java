package com.philia.flashsale.flashsale.campaignprojection.domain.model;

/** Lifecycle state stored in the local Campaign availability projection. */
public enum CampaignProjectionState {
    SCHEDULED,
    ACTIVE,
    ENDED,
    RECOVERY_REQUIRED
}
