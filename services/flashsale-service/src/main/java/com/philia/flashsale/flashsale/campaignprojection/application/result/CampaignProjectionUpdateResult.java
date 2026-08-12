package com.philia.flashsale.flashsale.campaignprojection.application.result;

/** Idempotent result returned by the atomic Redis projection boundary. */
public enum CampaignProjectionUpdateResult {
    APPLIED,
    NOOP_STALE,
    RECOVERY_REQUIRED
}
