package com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa;

import com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.entity.CampaignOutboxEventJpaEntity;
import com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.repository.CampaignOutboxEventJpaRepository;
import com.philia.flashsale.campaign.outbox.application.exception.CampaignOutboxEventInvalidStatusException;
import com.philia.flashsale.campaign.outbox.application.exception.CampaignOutboxEventNotFoundException;
import com.philia.flashsale.campaign.outbox.application.model.OutboxClaim;
import com.philia.flashsale.campaign.outbox.application.model.OutboxEventRecord;
import com.philia.flashsale.campaign.outbox.application.model.OutboxPublishStatus;
import com.philia.flashsale.campaign.outbox.application.model.OutboxRequeueResult;
import com.philia.flashsale.campaign.outbox.application.model.OutboxRetryPolicy;
import com.philia.flashsale.campaign.outbox.application.port.out.ClaimDueCampaignOutboxEventsPort;
import com.philia.flashsale.campaign.outbox.application.port.out.LoadCampaignOutboxEventPort;
import com.philia.flashsale.campaign.outbox.application.port.out.MarkCampaignOutboxPublishedPort;
import com.philia.flashsale.campaign.outbox.application.port.out.RecordCampaignOutboxFailurePort;
import com.philia.flashsale.campaign.outbox.application.port.out.RequeueCampaignOutboxEventPort;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns PostgreSQL-specific outbox recovery semantics. All updates are conditional on the current
 * lease owner so a second publisher instance cannot acknowledge or fail another worker's row.
 */
@Repository
public class OutboxPersistenceAdapter implements ClaimDueCampaignOutboxEventsPort,
        MarkCampaignOutboxPublishedPort, RecordCampaignOutboxFailurePort,
        RequeueCampaignOutboxEventPort, LoadCampaignOutboxEventPort {

    private static final String CLAIM_SQL = """
            WITH eligible AS (
                SELECT e.id
                FROM campaign_outbox_events e
                WHERE ((e.publish_status = 'PENDING' AND COALESCE(e.next_attempt_at, e.created_at) <= :now)
                    OR (e.publish_status = 'PROCESSING' AND e.claimed_until <= :now))
                  AND NOT EXISTS (
                      SELECT 1
                      FROM campaign_outbox_events predecessor
                      WHERE predecessor.aggregate_id = e.aggregate_id
                        AND (predecessor.aggregate_version < e.aggregate_version
                             OR (predecessor.aggregate_version = e.aggregate_version AND predecessor.id < e.id))
                        AND predecessor.publish_status <> 'PUBLISHED'
                  )
                ORDER BY e.created_at, e.id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE campaign_outbox_events e
               SET publish_status = 'PROCESSING',
                   claimed_by = :workerId,
                   claimed_until = :claimedUntil,
                   updated_at = :now
              FROM eligible
             WHERE e.id = eligible.id
            RETURNING e.id, e.aggregate_id, e.aggregate_version, e.event_type,
                      e.event_version, e.event_key, e.payload, e.trace_id,
                      e.occurred_at, e.claimed_until
            """;

    private final EntityManager entityManager;
    private final CampaignOutboxEventJpaRepository repository;

    public OutboxPersistenceAdapter(EntityManager entityManager,
            CampaignOutboxEventJpaRepository repository) {
        this.entityManager = entityManager;
        this.repository = repository;
    }

    @Override
    @Transactional
    public List<OutboxClaim> claimDue(String workerId, Instant now, Duration lease, int batchSize) {
        if (workerId == null || workerId.isBlank() || batchSize <= 0 || lease.isNegative()
                || lease.isZero()) {
            return List.of();
        }
        Instant claimedUntil = now.plus(lease);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(CLAIM_SQL)
                .setParameter("now", now)
                .setParameter("claimedUntil", claimedUntil)
                .setParameter("workerId", workerId)
                .setParameter("batchSize", batchSize)
                .getResultList();
        return rows.stream().map(row -> new OutboxClaim(
                (UUID) row[0],
                (UUID) row[1],
                ((Number) row[2]).longValue(),
                (String) row[3],
                ((Number) row[4]).intValue(),
                (String) row[5],
                (String) row[6],
                (String) row[7],
                instant(row[8]),
                instant(row[9]))).toList();
    }

    @Override
    @Transactional
    public boolean markPublished(UUID eventId, String workerId, Instant publishedAt) {
        Optional<CampaignOutboxEventJpaEntity> optional = repository.findLockedById(eventId);
        if (optional.isEmpty()) {
            return false;
        }
        CampaignOutboxEventJpaEntity entity = optional.get();
        if (!"PROCESSING".equals(entity.getPublishStatus())
                || !workerId.equals(entity.getClaimedBy())) {
            return false;
        }
        entity.setPublishStatus("PUBLISHED");
        entity.setPublishedAt(publishedAt);
        entity.setClaimedBy(null);
        entity.setClaimedUntil(null);
        entity.setNextAttemptAt(null);
        return true;
    }

    @Override
    @Transactional
    public boolean recordFailure(UUID eventId, String workerId, Instant now, String failureMessage,
            int maxAutomaticAttempts, Duration retryBackoffCap) {
        Optional<CampaignOutboxEventJpaEntity> optional = repository.findLockedById(eventId);
        if (optional.isEmpty()) {
            return false;
        }
        CampaignOutboxEventJpaEntity entity = optional.get();
        if (!"PROCESSING".equals(entity.getPublishStatus())
                || !workerId.equals(entity.getClaimedBy())) {
            return false;
        }
        int attempts = entity.getRetryCount() + 1;
        entity.setRetryCount(attempts);
        entity.setLastError(sanitize(failureMessage));
        entity.setClaimedBy(null);
        entity.setClaimedUntil(null);
        if (attempts >= maxAutomaticAttempts) {
            entity.setPublishStatus("FAILED");
            entity.setNextAttemptAt(null);
        } else {
            entity.setPublishStatus("PENDING");
            entity.setNextAttemptAt(OutboxRetryPolicy.nextAttemptAt(now, attempts, retryBackoffCap));
        }
        return true;
    }

    @Override
    @Transactional
    public OutboxRequeueResult requeue(UUID campaignId, UUID eventId, String actor, Instant now) {
        CampaignOutboxEventJpaEntity entity = repository.findLockedById(eventId)
                .filter(candidate -> campaignId.equals(candidate.getAggregateId()))
                .orElseThrow(() -> new CampaignOutboxEventNotFoundException(eventId));
        OutboxPublishStatus current = OutboxPublishStatus.valueOf(entity.getPublishStatus());
        if (current != OutboxPublishStatus.FAILED) {
            throw new CampaignOutboxEventInvalidStatusException(eventId, current);
        }
        entity.setPublishStatus("PENDING");
        entity.setRetryCount(0);
        entity.setNextAttemptAt(now);
        entity.setClaimedBy(null);
        entity.setClaimedUntil(null);
        entity.setRequeueCount(entity.getRequeueCount() + 1);
        entity.setRequeuedBy(actor);
        entity.setRequeuedAt(now);
        return new OutboxRequeueResult(entity.getId(), entity.getAggregateId(),
                OutboxPublishStatus.PENDING, entity.getRetryCount(), entity.getRequeueCount());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboxEventRecord> find(UUID campaignId, UUID eventId) {
        return repository.findByIdAndAggregateId(eventId, campaignId).map(this::toRecord);
    }

    private OutboxEventRecord toRecord(CampaignOutboxEventJpaEntity entity) {
        return new OutboxEventRecord(entity.getId(), entity.getAggregateId(), entity.getAggregateVersion(),
                entity.getEventType(), entity.getEventVersion(), entity.getEventKey(), entity.getPayload(),
                OutboxPublishStatus.valueOf(entity.getPublishStatus()), entity.getRetryCount(),
                entity.getNextAttemptAt(), entity.getOccurredAt(), entity.getPublishedAt(), entity.getLastError(),
                entity.getRequeueCount(), entity.getRequeuedBy(), entity.getRequeuedAt(), entity.getTraceId(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return value == null ? null : Instant.parse(value.toString());
    }

    /** Never persist credentials, tokens, SQL details, or an unbounded provider exception. */
    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "publication failed";
        }
        String sanitized = message.replaceAll("(?i)(bearer\\s+)[^\\s]+", "$1[REDACTED]")
                .replaceAll("(?i)(password|secret|token)=([^\\s,;]+)", "$1=[REDACTED]")
                .replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 1000 ? sanitized.substring(0, 1000) : sanitized;
    }
}
