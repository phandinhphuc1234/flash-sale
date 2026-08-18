package com.philia.flashsale.payment.payment.application.model.webhook;

/** Batch processing summary used by the scheduler and operational tests. */
public record ProviderEventProcessingResult(int claimed, int processed, int deferred, int manualReview) {
}
