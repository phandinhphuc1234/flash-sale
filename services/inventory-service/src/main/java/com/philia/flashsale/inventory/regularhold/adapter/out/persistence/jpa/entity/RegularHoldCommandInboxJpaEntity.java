package com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "regular_hold_command_inbox")
public class RegularHoldCommandInboxJpaEntity {
    @Id private UUID commandId;
    @Column(nullable = false, length = 100) private String commandType;
    @Column(nullable = false) private UUID orderId;
    @Column(nullable = false) private UUID holdId;
    @Column(nullable = false) private UUID purchaseRequestId;
    @Column(nullable = false) private long aggregateVersion;
    @Column(nullable = false, length = 64) private String payloadFingerprint;
    @Column(nullable = false, unique = true) private UUID resultEventId;
    @Column(nullable = false, length = 200) private String sourceTopic;
    @Column(nullable = false) private int sourcePartition;
    @Column(nullable = false) private long sourceOffset;
    @Column(nullable = false) private Instant processedAt;

    protected RegularHoldCommandInboxJpaEntity() { }

    public RegularHoldCommandInboxJpaEntity(UUID commandId, String commandType, UUID orderId, UUID holdId,
            UUID purchaseRequestId, long aggregateVersion, String payloadFingerprint, UUID resultEventId,
            String sourceTopic, int sourcePartition, long sourceOffset, Instant processedAt) {
        this.commandId = commandId;
        this.commandType = commandType;
        this.orderId = orderId;
        this.holdId = holdId;
        this.purchaseRequestId = purchaseRequestId;
        this.aggregateVersion = aggregateVersion;
        this.payloadFingerprint = payloadFingerprint;
        this.resultEventId = resultEventId;
        this.sourceTopic = sourceTopic;
        this.sourcePartition = sourcePartition;
        this.sourceOffset = sourceOffset;
        this.processedAt = processedAt;
    }

    public UUID getCommandId() { return commandId; }
    public String getCommandType() { return commandType; }
    public UUID getOrderId() { return orderId; }
    public UUID getHoldId() { return holdId; }
    public UUID getPurchaseRequestId() { return purchaseRequestId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
    public UUID getResultEventId() { return resultEventId; }
    public String getSourceTopic() { return sourceTopic; }
    public int getSourcePartition() { return sourcePartition; }
    public long getSourceOffset() { return sourceOffset; }
    public Instant getProcessedAt() { return processedAt; }
}
