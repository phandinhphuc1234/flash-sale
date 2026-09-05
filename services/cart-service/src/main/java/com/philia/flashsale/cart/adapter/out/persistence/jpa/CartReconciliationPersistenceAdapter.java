package com.philia.flashsale.cart.adapter.out.persistence.jpa;

import com.philia.flashsale.cart.application.command.ReconcilePurchasedCartSnapshotCommand;
import com.philia.flashsale.cart.application.port.out.ReconcilePurchasedCartSnapshotPort;
import com.philia.flashsale.cart.application.result.ReconcilePurchasedCartSnapshotResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Atomic conditional cleanup and inbox receipt for confirmed Cart purchases. */
@Repository
@ConditionalOnProperty(name = "cart.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CartReconciliationPersistenceAdapter implements ReconcilePurchasedCartSnapshotPort {
    private final JdbcTemplate jdbc;

    public CartReconciliationPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional
    public ReconcilePurchasedCartSnapshotResult apply(ReconcilePurchasedCartSnapshotCommand command) {
        String fingerprint = command.fingerprint();
        var byCommand = findInbox("command_id", command.commandId());
        if (byCommand != null) return replayOrConflict(byCommand, fingerprint);
        var byOrder = findInbox("order_id", command.orderId());
        if (byOrder != null) return replayOrConflict(byOrder, fingerprint);

        Map<String, Object> cart = jdbc.query("SELECT owner_id, version FROM carts WHERE id = ? FOR UPDATE",
                rs -> rs.next() ? Map.of("owner_id", rs.getObject("owner_id", UUID.class),
                        "version", rs.getLong("version")) : null, command.cartId());
        if (cart == null || !command.ownerId().equals(cart.get("owner_id"))) {
            throw new IllegalStateException("Cart identity or owner does not match reconciliation command");
        }
        int removed = 0;
        for (var item : command.items()) {
            removed += jdbc.update("DELETE FROM cart_items WHERE cart_id = ? AND variant_id = ? "
                            + "AND quantity = ? AND version = ?", command.cartId(), item.variantId(),
                    item.quantity(), item.itemVersion());
        }
        if (removed > 0) {
            jdbc.update("UPDATE carts SET version = version + 1, updated_at = ? WHERE id = ?",
                    Timestamp.from(command.confirmedAt()), command.cartId());
        }
        jdbc.update("""
                INSERT INTO cart_reconciliation_inbox
                    (command_id, order_id, cart_id, owner_id, payload_fingerprint, source_topic,
                     source_partition, source_offset, removed_item_count, processed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, command.commandId(), command.orderId(), command.cartId(), command.ownerId(), fingerprint,
                command.sourceTopic(), command.sourcePartition(), command.sourceOffset(), removed,
                Timestamp.from(Instant.now()));
        var outcome = removed == command.items().size()
                ? ReconcilePurchasedCartSnapshotResult.Outcome.APPLIED
                : ReconcilePurchasedCartSnapshotResult.Outcome.PARTIAL_NOOP;
        return new ReconcilePurchasedCartSnapshotResult(outcome, removed);
    }

    private Map<String, Object> findInbox(String column, UUID id) {
        return jdbc.query("SELECT payload_fingerprint, removed_item_count FROM cart_reconciliation_inbox WHERE "
                        + column + " = ?", rs -> rs.next() ? Map.of("fingerprint", rs.getString(1),
                        "removed", rs.getInt(2)) : null, id);
    }

    private ReconcilePurchasedCartSnapshotResult replayOrConflict(Map<String, Object> existing,
            String fingerprint) {
        if (!fingerprint.equals(existing.get("fingerprint"))) {
            return new ReconcilePurchasedCartSnapshotResult(
                    ReconcilePurchasedCartSnapshotResult.Outcome.CONFLICT, 0);
        }
        return new ReconcilePurchasedCartSnapshotResult(
                ReconcilePurchasedCartSnapshotResult.Outcome.REPLAYED, (Integer) existing.get("removed"));
    }
}
