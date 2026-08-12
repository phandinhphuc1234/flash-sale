package com.philia.flashsale.campaign.campaign.domain.model;

/** Monotonic lifecycle states owned by the Campaign aggregate. */
public enum CampaignStatus {
    DRAFT,
    SCHEDULED,
    ACTIVE,
    ENDED;

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isTerminal() {
        return this == ENDED;
    }

    public boolean canTransitionTo(CampaignStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == SCHEDULED;
            case SCHEDULED -> target == ACTIVE;
            case ACTIVE -> target == ENDED;
            case ENDED -> false;
        };
    }
}
