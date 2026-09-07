package com.philia.flashsale.inventory.regularhold.application.model;

import com.philia.flashsale.inventory.regularhold.application.command.ReleaseRegularStockHoldCommand;

/** Canonical, validated release command plus its Kafka source position. */
public record ReleaseRegularHoldMessage(
        ReleaseRegularStockHoldCommand command,
        long aggregateVersion,
        String payloadFingerprint,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String traceparent,
        String tracestate
) {
    public java.util.UUID commandId() {
        return command.commandId();
    }
}
