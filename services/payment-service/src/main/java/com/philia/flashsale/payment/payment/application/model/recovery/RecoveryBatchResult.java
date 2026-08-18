package com.philia.flashsale.payment.payment.application.model.recovery;

/** Aggregate result for one bounded worker poll. */
public record RecoveryBatchResult(int claimed, int converged, int deferred, int manualReview) {
}
