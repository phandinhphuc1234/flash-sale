package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/** Durable replay identity retained independently from the purchase audit rows. */
@Entity
@Table(name = "purchase_idempotency_records")
public class PurchaseIdempotencyJpaEntity {
    @EmbeddedId private PurchaseIdempotencyJpaId id;
    @JdbcTypeCode(Types.CHAR) @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "purchase_request_id", nullable = false, unique = true) private UUID purchaseRequestId;
    @Column(name = "reservation_id", nullable = false) private UUID reservationId;
    @Column(name = "retained_until", nullable = false) private Instant retainedUntil;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PurchaseIdempotencyJpaEntity() { }

    public static PurchaseIdempotencyJpaEntity accepted(AcceptedReservationSnapshot snapshot) {
        var entity = new PurchaseIdempotencyJpaEntity();
        entity.id = new PurchaseIdempotencyJpaId(snapshot.userId(), snapshot.campaignId(), snapshot.idempotencyKeyHash());
        entity.requestHash = snapshot.requestHash();
        entity.purchaseRequestId = snapshot.purchaseRequestId();
        entity.reservationId = snapshot.reservationId();
        entity.retainedUntil = snapshot.retainedUntil();
        entity.createdAt = snapshot.acceptedAt();
        return entity;
    }

    public PurchaseIdempotencyJpaId getId() { return id; }
    public String getRequestHash() { return requestHash; }
    public UUID getPurchaseRequestId() { return purchaseRequestId; }
    public UUID getReservationId() { return reservationId; }
    public Instant getRetainedUntil() { return retainedUntil; }
}
