package com.philia.flashsale.inventory.regularhold.application.usecase;

import com.philia.flashsale.inventory.configuration.InventoryRegularHoldProperties;
import com.philia.flashsale.inventory.observability.InventoryObservability;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldFactItem;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldFactOutboxEvent;
import com.philia.flashsale.inventory.regularhold.application.port.in.ExpireDueRegularStockHoldsUseCase;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadDueRegularStockHoldsPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.RecordRegularHoldOutboxPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.SaveRegularStockHoldPort;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Expires a bounded set of due holds and records recoverable facts in one local transaction. */
@Service
public class RegularStockHoldExpiryService implements ExpireDueRegularStockHoldsUseCase {
    private final LoadDueRegularStockHoldsPort holds;
    private final SaveRegularStockHoldPort saveHold;
    private final RecordRegularHoldOutboxPort recordOutbox;
    private final InventoryRegularHoldProperties properties;
    private final Clock clock;
    private final InventoryObservability observability;

    /** Backward-compatible constructor for direct use-case tests. */
    public RegularStockHoldExpiryService(LoadDueRegularStockHoldsPort holds, SaveRegularStockHoldPort saveHold,
            RecordRegularHoldOutboxPort recordOutbox, InventoryRegularHoldProperties properties, Clock inventoryClock) {
        this(holds, saveHold, recordOutbox, properties, inventoryClock, InventoryObservability.noop());
    }

    @Autowired
    public RegularStockHoldExpiryService(LoadDueRegularStockHoldsPort holds, SaveRegularStockHoldPort saveHold,
            RecordRegularHoldOutboxPort recordOutbox, InventoryRegularHoldProperties properties,
            Clock inventoryClock, InventoryObservability observability) {
        this.holds = holds;
        this.saveHold = saveHold;
        this.recordOutbox = recordOutbox;
        this.properties = properties;
        this.clock = inventoryClock;
        this.observability = observability;
    }

    @Override
    @Transactional
    public int expireDue() {
        Instant now = clock.instant();
        int expired = 0;
        var due = holds.findDueForUpdate(now, properties.getExpiryBatchSize());
        Duration oldestDueAge = due.stream()
                .map(RegularStockHold::expiresAt)
                .map(expiresAt -> Duration.between(expiresAt, now))
                .filter(age -> !age.isNegative())
                .max(Comparator.naturalOrder())
                .orElse(Duration.ZERO);
        observability.recordExpiryDiagnostics(due.size(), oldestDueAge);
        for (RegularStockHold hold : due) {
            if (!hold.isHeld() || !hold.isExpiredAt(now)) {
                observability.recordExpiryOutcome("skipped");
                continue;
            }
            hold.expire(now);
            RegularStockHold saved = saveHold.save(hold);
            recordOutbox.record(new RegularHoldFactOutboxEvent(UUID.randomUUID(), "RegularStockHoldExpired",
                    saved.version(), saved.id(), saved.purchaseRequestId(), saved.orderId(), saved.purchaseRequestId(),
                    null, null, saved.status(), saved.items().stream()
                            .map(item -> new RegularHoldFactItem(item.variantId(), item.quantity())).toList(),
                    null, saved.expiredAt(), "HOLD_TTL_EXPIRED"));
            observability.recordHoldState("expired");
            observability.recordExpiryOutcome("expired");
            expired++;
        }
        if (due.isEmpty()) {
            observability.recordExpiryOutcome("empty");
        }
        return expired;
    }
}
