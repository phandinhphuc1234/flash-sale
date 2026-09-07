package com.philia.flashsale.inventory.regularhold.application.model;

import com.philia.flashsale.inventory.regularhold.application.command.ConfirmRegularStockHoldCommand;
import java.util.UUID;

/** Canonical, validated inbound command plus Kafka source position. */
public record ConfirmRegularHoldMessage(
        ConfirmRegularStockHoldCommand command,
        long aggregateVersion,
        String payloadFingerprint,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String traceparent,
        String tracestate
) {
    public UUID commandId() {
        return command.commandId();
    }
}
