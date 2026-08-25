package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Durable deduplication record for reservation commands from Order. */
@Entity
@Table(name = "reservation_command_inbox")
public class ReservationCommandInboxJpaEntity {
    @Id @Column(name = "command_id", nullable = false) private UUID commandId;
    @Column(name = "command_type", nullable = false, length = 32) private String commandType;
    @Column(name = "command_version", nullable = false) private int commandVersion;
    @Column(nullable = false, length = 100) private String producer;
    @Column(name = "saga_id", nullable = false) private UUID sagaId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "purchase_request_id", nullable = false) private UUID purchaseRequestId;
    @Column(name = "reservation_id", nullable = false) private UUID reservationId;
    @Column(name = "payload_fingerprint", nullable = false, length = 64) private String payloadFingerprint;
    @Column(name = "result_event_id") private UUID resultEventId;
    @Column(name = "source_topic", nullable = false, length = 200) private String sourceTopic;
    @Column(name = "source_partition", nullable = false) private int sourcePartition;
    @Column(name = "source_offset", nullable = false) private long sourceOffset;
    @Column(name = "processed_at", nullable = false) private Instant processedAt;

    protected ReservationCommandInboxJpaEntity() { }

    public static ReservationCommandInboxJpaEntity confirmed(ConfirmReservationCommand command,
            UUID resultEventId, Instant processedAt) {
        var entity = new ReservationCommandInboxJpaEntity();
        entity.commandId = command.commandId();
        entity.commandType = command.commandType();
        entity.commandVersion = command.commandVersion();
        entity.producer = command.producer();
        entity.sagaId = command.sagaId();
        entity.orderId = command.orderId();
        entity.purchaseRequestId = command.purchaseRequestId();
        entity.reservationId = command.reservationId();
        entity.payloadFingerprint = command.payloadFingerprint();
        entity.resultEventId = resultEventId;
        entity.sourceTopic = command.sourceTopic();
        entity.sourcePartition = command.sourcePartition();
        entity.sourceOffset = command.sourceOffset();
        entity.processedAt = processedAt;
        return entity;
    }

    public UUID getCommandId() { return commandId; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
    public UUID getResultEventId() { return resultEventId; }
    public UUID getReservationId() { return reservationId; }
}
