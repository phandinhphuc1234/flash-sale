package com.philia.flashsale.campaign.outbox.application.model;

/** Durable publication states owned by the Campaign outbox workflow. */
public enum OutboxPublishStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
