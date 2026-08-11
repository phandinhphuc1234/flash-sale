package com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign;

import java.time.Duration;

/** Adapter-only representation of a stable Campaign HTTP failure. */
final class CampaignSnapshotRemoteException extends RuntimeException {

    enum Failure {
        SECURITY,
        NOT_FOUND,
        NOT_RECOVERABLE,
        RATE_LIMITED,
        UNAVAILABLE
    }

    private final Failure failure;
    private final int status;
    private final Duration retryAfter;

    CampaignSnapshotRemoteException(Failure failure, int status, Duration retryAfter) {
        super("Campaign snapshot HTTP failure: " + status);
        this.failure = failure;
        this.status = status;
        this.retryAfter = retryAfter;
    }

    Failure failure() {
        return failure;
    }

    int status() {
        return status;
    }

    Duration retryAfter() {
        return retryAfter;
    }
}
