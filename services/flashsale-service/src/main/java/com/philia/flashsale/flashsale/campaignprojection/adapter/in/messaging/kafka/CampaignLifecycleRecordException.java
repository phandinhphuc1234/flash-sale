package com.philia.flashsale.flashsale.campaignprojection.adapter.in.messaging.kafka;

/** Signals an invalid or unsupported Campaign lifecycle record at the Kafka boundary. */
final class CampaignLifecycleRecordException extends IllegalArgumentException {
    CampaignLifecycleRecordException(String message) {
        super(message);
    }
}
