package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable publication intent created in the same transaction as durable acceptance. */
@Entity
@Table(name = "flash_sale_outbox_events")
public class PurchaseEventOutboxJpaEntity {
    private static final String PENDING = "PENDING";
    private static final String TRACEPARENT = "traceparent";
    private static final String TRACESTATE = "tracestate";

    @Id @Column(name = "event_id") private UUID eventId;
    @Column(name = "aggregate_type", nullable = false, length = 64) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "aggregate_version", nullable = false) private long aggregateVersion;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(name = "event_version", nullable = false) private int eventVersion;
    @Column(name = "causation_id") private UUID causationId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private Map<String, Object> payload;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "claimed_by", length = 200) private String claimedBy;
    @Column(name = "claim_until") private Instant claimUntil;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PurchaseEventOutboxJpaEntity() { }

    public static PurchaseEventOutboxJpaEntity accepted(AcceptedReservationSnapshot snapshot) {
        var entity = new PurchaseEventOutboxJpaEntity();
        entity.eventId = snapshot.eventId();
        entity.aggregateType = "PURCHASE_REQUEST";
        entity.aggregateId = snapshot.purchaseRequestId();
        entity.aggregateVersion = 1;
        entity.eventType = "PurchaseAccepted";
        entity.eventVersion = 1;
        entity.causationId = null;
        entity.payload = new LinkedHashMap<>();
        entity.payload.put("purchaseRequestId", snapshot.purchaseRequestId().toString());
        entity.payload.put("reservationId", snapshot.reservationId().toString());
        entity.payload.put("campaignId", snapshot.campaignId().toString());
        entity.payload.put("variantId", snapshot.variantId().toString());
        entity.payload.put("userId", snapshot.userId().toString());
        entity.payload.put("inventoryAllocationId", snapshot.inventoryAllocationId().toString());
        entity.payload.put("skuSnapshot", snapshot.skuSnapshot());
        entity.payload.put("unitPrice", snapshot.unitPrice().setScale(4).toPlainString());
        entity.payload.put("currency", snapshot.currency());
        entity.payload.put("quantity", snapshot.quantity());
        entity.payload.put("acceptedAt", snapshot.acceptedAt().toString());
        entity.payload.put("expiresAt", snapshot.expiresAt().toString());
        entity.payload.put(TRACEPARENT, snapshot.traceparent());
        entity.payload.put(TRACESTATE, snapshot.tracestate());
        entity.status = PENDING;
        entity.attemptCount = 0;
        entity.nextAttemptAt = snapshot.acceptedAt();
        entity.createdAt = snapshot.acceptedAt();
        entity.updatedAt = snapshot.acceptedAt();
        return entity;
    }

    /** Creates a stable confirmed outcome scoped to the causing confirm command. */
    public static PurchaseEventOutboxJpaEntity confirmed(ConfirmReservationCommand command,
            FlashSaleReservationJpaEntity reservation, UUID resultEventId, Instant confirmedAt) {
        return confirmed(command, reservation, resultEventId, confirmedAt,
                Math.max(1, reservation.getVersion() + 1));
    }

    /** Creates a confirmed current-state result without inventing a new aggregate version. */
    public static PurchaseEventOutboxJpaEntity confirmed(ConfirmReservationCommand command,
            FlashSaleReservationJpaEntity reservation, UUID resultEventId, Instant confirmedAt,
            long aggregateVersion) {
        var entity = newPurchaseReservationResult(resultEventId, reservation, aggregateVersion,
                "PurchaseReservationConfirmed", command.commandId(), confirmedAt);
        entity.payload = reservationPayload(command.sagaId(), command.orderId(), command.purchaseRequestId(),
                command.reservationId(), command.traceparent(), command.tracestate());
        entity.payload.put("paymentId", command.paymentId().toString());
        entity.payload.put("confirmedAt", confirmedAt.toString());
        return entity;
    }

    /** Creates a released/expired result caused by a late confirm command. */
    public static PurchaseEventOutboxJpaEntity currentStateReleased(ConfirmReservationCommand command,
            FlashSaleReservationJpaEntity reservation, UUID resultEventId, Instant observedAt,
            String reservationStatus, String reason, long aggregateVersion) {
        var entity = newPurchaseReservationResult(resultEventId, reservation, aggregateVersion,
                "PurchaseReservationReleased", command.commandId(), observedAt);
        entity.payload = reservationPayload(command.sagaId(), command.orderId(), command.purchaseRequestId(),
                command.reservationId(), command.traceparent(), command.tracestate());
        entity.payload.put("reservationStatus", reservationStatus);
        entity.payload.put("reason", reason);
        entity.payload.put("releasedAt", observedAt.toString());
        return entity;
    }

    /** Creates a released/expired reservation result scoped to the causing release command. */
    public static PurchaseEventOutboxJpaEntity released(ReleaseReservationCommand command,
            FlashSaleReservationJpaEntity reservation, UUID resultEventId, Instant releasedAt, String status) {
        var entity = newPurchaseReservationResult(resultEventId, reservation, reservation.getVersion() + 1,
                "PurchaseReservationReleased", command.commandId(), releasedAt);
        entity.payload = reservationPayload(command.sagaId(), command.orderId(), command.purchaseRequestId(),
                command.reservationId(), command.traceparent(), command.tracestate());
        entity.payload.put("reservationStatus", status);
        entity.payload.put("reason", command.reason());
        entity.payload.put("releasedAt", releasedAt.toString());
        return entity;
    }

    private static PurchaseEventOutboxJpaEntity newPurchaseReservationResult(UUID eventId,
            FlashSaleReservationJpaEntity reservation, long aggregateVersion, String eventType,
            UUID causationId, Instant occurredAt) {
        var entity = new PurchaseEventOutboxJpaEntity();
        entity.eventId = eventId;
        entity.aggregateType = "PURCHASE_RESERVATION";
        entity.aggregateId = reservation.getId();
        entity.aggregateVersion = Math.max(1, aggregateVersion);
        entity.eventType = eventType;
        entity.eventVersion = 1;
        entity.causationId = causationId;
        entity.status = PENDING;
        entity.attemptCount = 0;
        entity.nextAttemptAt = occurredAt;
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        return entity;
    }

    private static Map<String, Object> reservationPayload(UUID sagaId, UUID orderId,
            UUID purchaseRequestId, UUID reservationId, String traceparent, String tracestate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sagaId", sagaId.toString());
        payload.put("orderId", orderId.toString());
        payload.put("purchaseRequestId", purchaseRequestId.toString());
        payload.put("reservationId", reservationId.toString());
        payload.put(TRACEPARENT, traceparent);
        payload.put(TRACESTATE, tracestate);
        return payload;
    }

    public UUID getEventId() { return eventId; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public long getAggregateVersion() { return aggregateVersion; }
    public UUID getCausationId() { return causationId; }
    public String getStatus() { return status; }
    public Map<String, Object> getPayload() { return payload; }
}
