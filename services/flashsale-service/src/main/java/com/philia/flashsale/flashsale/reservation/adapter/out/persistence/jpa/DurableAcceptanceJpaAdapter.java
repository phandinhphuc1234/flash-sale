package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseEventOutboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseIdempotencyJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseIdempotencyJpaId;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseRequestJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.mapper.ReservationPersistenceMapper;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseIdempotencyJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseRequestJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One transaction owns all four durable acceptance rows; no external adapter is called here. */
@Component
@ConditionalOnBean(PurchaseRequestJpaRepository.class)
public class DurableAcceptanceJpaAdapter implements PersistAcceptedPurchasePort {
    private final PurchaseRequestJpaRepository requests;
    private final FlashSaleReservationJpaRepository reservations;
    private final PurchaseIdempotencyJpaRepository idempotency;
    private final PurchaseEventOutboxJpaRepository outbox;
    private final ReservationPersistenceMapper mapper;
    private final Clock clock;
    private final EntityManager entityManager;

    public DurableAcceptanceJpaAdapter(PurchaseRequestJpaRepository requests,
            FlashSaleReservationJpaRepository reservations, PurchaseIdempotencyJpaRepository idempotency,
            PurchaseEventOutboxJpaRepository outbox) {
        this(requests, reservations, idempotency, outbox, Clock.systemUTC(), null);
    }

    @Autowired
    public DurableAcceptanceJpaAdapter(PurchaseRequestJpaRepository requests,
            FlashSaleReservationJpaRepository reservations, PurchaseIdempotencyJpaRepository idempotency,
            PurchaseEventOutboxJpaRepository outbox, Clock clock, EntityManager entityManager) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.mapper = new ReservationPersistenceMapper();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void persist(AcceptedReservationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var key = new PurchaseIdempotencyJpaId(snapshot.userId(), snapshot.campaignId(), snapshot.idempotencyKeyHash());
        lockLogicalRequest(key);
        var existing = idempotency.findById(key);
        if (existing.isPresent()) {
            assertSameRequest(existing.get(), snapshot);
            return;
        }

        var existingRequest = requests.findById(snapshot.purchaseRequestId());
        if (existingRequest.isPresent()) {
            if (!existingRequest.get().getRequestHash().equals(snapshot.requestHash())
                    || !existingRequest.get().getReservationId().equals(snapshot.reservationId())) {
                throw new IllegalStateException("purchase request identity conflicts with an existing request");
            }
            // A terminal EXPIRED request is deliberately not resurrected by a late Stream replay.
            return;
        }

        Instant now = clock.instant();
        if (!now.isBefore(snapshot.expiresAt())) {
            requests.save(mapper.expiredRequest(snapshot, now));
            idempotency.save(mapper.idempotency(snapshot));
            return;
        }

        requests.save(mapper.acceptedRequest(snapshot));
        reservations.save(mapper.reservedReservation(snapshot));
        idempotency.save(mapper.idempotency(snapshot));
        outbox.save(mapper.acceptedOutbox(snapshot));
    }

    private void assertSameRequest(PurchaseIdempotencyJpaEntity existing, AcceptedReservationSnapshot snapshot) {
        if (!existing.getRequestHash().equals(snapshot.requestHash())
                || !existing.getPurchaseRequestId().equals(snapshot.purchaseRequestId())
                || !existing.getReservationId().equals(snapshot.reservationId())) {
            throw new IllegalStateException("idempotency key conflicts with an existing request");
        }
    }

    private void lockLogicalRequest(PurchaseIdempotencyJpaId key) {
        if (entityManager != null) {
            entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))")
                    .setParameter(1, key.getUserId() + ":" + key.getCampaignId() + ":" + key.getIdempotencyKeyHash())
                    .getSingleResult();
        }
    }
}
