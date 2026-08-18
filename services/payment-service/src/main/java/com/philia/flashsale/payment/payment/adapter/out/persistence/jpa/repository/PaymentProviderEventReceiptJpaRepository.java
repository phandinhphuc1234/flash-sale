package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentProviderEventReceiptJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Receipt storage plus adapter-local worker claim query. */
public interface PaymentProviderEventReceiptJpaRepository
        extends JpaRepository<PaymentProviderEventReceiptJpaEntity, UUID> {

    /**
     * Looks up the provider event identity before accepting a delivery. Stripe can retry the
     * same event, so this lookup lets the application return a duplicate acknowledgement without
     * scheduling a second processing attempt.
     */
    Optional<PaymentProviderEventReceiptJpaEntity> findByProviderEventId(String providerEventId);

    /**
     * Inserts the first durable receipt for a provider event.
     *
     * The unique provider-event constraint and {@code ON CONFLICT DO NOTHING} make concurrent
     * webhook deliveries converge on one row without turning a duplicate into a transaction
     * failure. The return value is {@code 1} for a new receipt and {@code 0} when it already
     * exists; callers re-read the canonical row after either result.
     */
    @Modifying
    @Query(value = """
            -- Persist only the first delivery; duplicate Stripe retries are harmless.
            insert into payment_provider_event_receipts
                (id, provider_event_id, provider_event_type, provider_api_version, live_mode,
                 provider_object_id, payment_id, attempt_id, order_id, provider_created_at,
                 verified_at, processing_status, attempt_count, next_attempt_at)
            values (:id, :eventId, :eventType, :apiVersion, :liveMode,
                    :objectId, :paymentId, :attemptId, :orderId, :providerCreatedAt,
                    :verifiedAt, :processingStatus, 0, :verifiedAt)
            on conflict (provider_event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("eventId") String eventId,
            @Param("eventType") String eventType, @Param("apiVersion") String apiVersion,
            @Param("liveMode") boolean liveMode, @Param("objectId") String objectId,
            @Param("paymentId") UUID paymentId, @Param("attemptId") UUID attemptId,
            @Param("orderId") UUID orderId, @Param("providerCreatedAt") Instant providerCreatedAt,
            @Param("verifiedAt") Instant verifiedAt, @Param("processingStatus") String processingStatus);

    /**
     * Selects receipts that a worker may claim.
     *
     * {@code PENDING} rows are due for their first/next attempt. {@code IN_PROGRESS} rows whose
     * lease expired are eligible for crash recovery. Rows are locked and skipped per worker so two
     * workers cannot claim the same receipt in the same transaction; the adapter then writes the
     * new lease before the transaction commits.
     */
    @Query(value = """
            -- Pending work or work whose previous worker lease has expired.
            select * from payment_provider_event_receipts
            where (processing_status = 'PENDING' and next_attempt_at <= :now)
               or (processing_status = 'IN_PROGRESS' and lease_until < :now)
            order by next_attempt_at, verified_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<PaymentProviderEventReceiptJpaEntity> claimCandidates(
            @Param("now") Instant now, @Param("batchSize") int batchSize);
}
