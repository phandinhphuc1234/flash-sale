package com.philia.flashsale.flashsale.outbox.adapter.out.persistence.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import com.philia.flashsale.flashsale.outbox.application.port.ClaimOutboxEventsPort;
import com.philia.flashsale.flashsale.outbox.application.port.UpdateOutboxPublicationPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-only lease/ack adapter; no JPA reservation types cross this boundary. */
public class FlashSaleOutboxPersistenceAdapter implements ClaimOutboxEventsPort, UpdateOutboxPublicationPort {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FlashSaleOutboxPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public List<OutboxEvent> claim(String workerId, Instant now, int batchSize, Duration lease) {
        Instant claimUntil = now.plus(lease);
        List<OutboxEvent> due = jdbc.query("""
                SELECT event_id, aggregate_type, aggregate_id, aggregate_version,
                       event_type, event_version, payload::text AS payload, status,
                       attempt_count, next_attempt_at, claimed_by, claim_until,
                       published_at, last_error, created_at, updated_at
                  FROM flash_sale_outbox_events
                 WHERE (status = 'PENDING' AND next_attempt_at <= ?)
                    OR (status = 'PROCESSING' AND claim_until <= ?)
                 ORDER BY created_at ASC, event_id ASC
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """, this::mapRow, Timestamp.from(now), Timestamp.from(now), batchSize);
        for (OutboxEvent event : due) {
            jdbc.update("""
                    UPDATE flash_sale_outbox_events
                       SET status = 'PROCESSING', claimed_by = ?, claim_until = ?,
                           attempt_count = attempt_count + 1, updated_at = ?
                     WHERE event_id = ?
                    """, workerId, Timestamp.from(claimUntil), Timestamp.from(now), event.eventId());
        }
        return due.stream().map(event -> new OutboxEvent(event.eventId(), event.aggregateType(),
                event.aggregateId(), event.aggregateVersion(), event.eventType(), event.eventVersion(),
                event.payload(), "PROCESSING", event.attemptCount() + 1, claimUntil, workerId,
                claimUntil, event.publishedAt(), event.lastError(), event.createdAt(), now)).toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID eventId, Instant publishedAt) {
        jdbc.update("""
                UPDATE flash_sale_outbox_events
                   SET status = 'PUBLISHED', published_at = ?, claimed_by = NULL,
                       claim_until = NULL, updated_at = ?
                 WHERE event_id = ? AND status = 'PROCESSING'
                """, Timestamp.from(publishedAt), Timestamp.from(publishedAt), eventId);
    }

    @Override
    @Transactional
    public void markFailed(UUID eventId, Instant failedAt, Instant nextAttemptAt, String sanitizedError) {
        jdbc.update("""
                UPDATE flash_sale_outbox_events
                   SET status = 'PENDING', next_attempt_at = ?, claimed_by = NULL,
                       claim_until = NULL, last_error = ?, updated_at = ?
                 WHERE event_id = ? AND status = 'PROCESSING'
                """, Timestamp.from(nextAttemptAt), limitError(sanitizedError), Timestamp.from(failedAt), eventId);
    }

    private OutboxEvent mapRow(ResultSet result, int row) throws SQLException {
        return new OutboxEvent(
                result.getObject("event_id", UUID.class),
                result.getString("aggregate_type"),
                result.getObject("aggregate_id", UUID.class),
                result.getLong("aggregate_version"),
                result.getString("event_type"),
                result.getInt("event_version"),
                parsePayload(result.getString("payload")),
                result.getString("status"),
                result.getInt("attempt_count"),
                result.getTimestamp("next_attempt_at").toInstant(),
                result.getString("claimed_by"),
                instant(result, "claim_until"),
                instant(result, "published_at"),
                result.getString("last_error"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }

    private Map<String, Object> parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("outbox payload cannot be decoded", exception);
        }
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        var timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String limitError(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
