package com.philia.flashsale.order.outbox.adapter.out.persistence;

import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.ClaimOrderOutboxEventsPort;
import com.philia.flashsale.order.outbox.application.port.UpdateOrderOutboxPublicationPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL lease/ack adapter for the service-owned Order outbox. */
public class OrderOutboxPersistenceAdapter
        implements ClaimOrderOutboxEventsPort, UpdateOrderOutboxPublicationPort {

    private final JdbcTemplate jdbc;

    public OrderOutboxPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public List<OrderOutboxEvent> claim(String workerId, Instant now, int batchSize, Duration lease) {
        Instant claimUntil = now.plus(lease);
        List<OrderOutboxEvent> due = jdbc.query("""
                SELECT event_id, aggregate_type, aggregate_id, aggregate_version,
                       event_type, event_version, event_key, correlation_id, causation_id,
                       payload::text AS payload, traceparent, tracestate, status,
                       attempt_count, next_attempt_at, claimed_by, claim_until,
                       published_at, last_error, occurred_at, created_at, updated_at
                  FROM order_outbox_events
                 WHERE (status = 'PENDING' AND next_attempt_at <= ?)
                    OR (status = 'IN_PROGRESS' AND claim_until <= ?)
                 ORDER BY created_at ASC, event_id ASC
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """, this::mapRow, Timestamp.from(now), Timestamp.from(now), batchSize);
        for (OrderOutboxEvent event : due) {
            jdbc.update("""
                    UPDATE order_outbox_events
                       SET status = 'IN_PROGRESS', claimed_by = ?, claim_until = ?,
                           attempt_count = attempt_count + 1, updated_at = ?
                     WHERE event_id = ?
                    """, workerId, Timestamp.from(claimUntil), Timestamp.from(now), event.eventId());
        }
        return due.stream()
                .map(event -> claimed(event, workerId, claimUntil, now))
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID eventId, String workerId, Instant publishedAt) {
        jdbc.update("""
                UPDATE order_outbox_events
                   SET status = 'PUBLISHED', published_at = ?, claimed_by = NULL,
                       claim_until = NULL, updated_at = ?
                 WHERE event_id = ? AND status = 'IN_PROGRESS' AND claimed_by = ?
                """, Timestamp.from(publishedAt), Timestamp.from(publishedAt), eventId, workerId);
    }

    @Override
    @Transactional
    public void markFailed(UUID eventId, String workerId, Instant failedAt, Instant nextAttemptAt,
            String sanitizedError) {
        jdbc.update("""
                UPDATE order_outbox_events
                   SET status = 'PENDING', next_attempt_at = ?, claimed_by = NULL,
                       claim_until = NULL, last_error = ?, updated_at = ?
                 WHERE event_id = ? AND status = 'IN_PROGRESS' AND claimed_by = ?
                """, Timestamp.from(nextAttemptAt), limitError(sanitizedError), Timestamp.from(failedAt),
                eventId, workerId);
    }

    private OrderOutboxEvent claimed(OrderOutboxEvent event, String workerId, Instant claimUntil, Instant now) {
        return new OrderOutboxEvent(event.eventId(), event.aggregateType(), event.aggregateId(),
                event.aggregateVersion(), event.eventType(), event.eventVersion(), event.eventKey(),
                event.correlationId(), event.causationId(), event.payload(), event.traceparent(), event.tracestate(),
                "IN_PROGRESS", event.attemptCount() + 1, claimUntil, workerId, claimUntil, event.publishedAt(),
                event.lastError(), event.occurredAt(), event.createdAt(), now);
    }

    private OrderOutboxEvent mapRow(ResultSet result, int row) throws SQLException {
        return new OrderOutboxEvent(
                result.getObject("event_id", UUID.class),
                result.getString("aggregate_type"),
                result.getObject("aggregate_id", UUID.class),
                result.getLong("aggregate_version"),
                result.getString("event_type"),
                result.getInt("event_version"),
                result.getString("event_key"),
                result.getObject("correlation_id", UUID.class),
                result.getObject("causation_id", UUID.class),
                result.getString("payload"),
                result.getString("traceparent"),
                result.getString("tracestate"),
                result.getString("status"),
                result.getInt("attempt_count"),
                timestamp(result, "next_attempt_at"),
                result.getString("claimed_by"),
                timestampOrNull(result, "claim_until"),
                timestampOrNull(result, "published_at"),
                result.getString("last_error"),
                timestamp(result, "occurred_at"),
                timestamp(result, "created_at"),
                timestamp(result, "updated_at"));
    }

    private Instant timestamp(ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private Instant timestampOrNull(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String limitError(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
