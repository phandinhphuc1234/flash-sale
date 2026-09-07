package com.philia.flashsale.inventory.regularhold.application.usecase;

import com.philia.flashsale.inventory.regularhold.application.exception.RegularHoldCommandConflictException;
import com.philia.flashsale.inventory.regularhold.application.model.ReleaseRegularHoldMessage;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldCommandInboxEntry;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldFactItem;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldFactOutboxEvent;
import com.philia.flashsale.inventory.regularhold.application.port.in.ProcessReleaseRegularHoldCommandUseCase;
import com.philia.flashsale.inventory.regularhold.application.port.in.ReleaseRegularStockHoldUseCase;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadRegularHoldCommandInboxPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.RecordRegularHoldOutboxPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.ReplayRegularHoldOutboxPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.SaveRegularHoldCommandInboxPort;
import com.philia.flashsale.inventory.regularhold.application.result.RegularStockHoldReleaseResult;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits release/expiry state, Inbox identity, and a durable outcome fact atomically. */
@Service
public class RegularHoldReleaseCommandProcessingService implements ProcessReleaseRegularHoldCommandUseCase {
    private final LoadRegularHoldCommandInboxPort inbox;
    private final SaveRegularHoldCommandInboxPort saveInbox;
    private final ReleaseRegularStockHoldUseCase releaseHold;
    private final RecordRegularHoldOutboxPort recordOutbox;
    private final ReplayRegularHoldOutboxPort replayOutbox;
    private final Clock clock;

    public RegularHoldReleaseCommandProcessingService(LoadRegularHoldCommandInboxPort inbox,
            SaveRegularHoldCommandInboxPort saveInbox, ReleaseRegularStockHoldUseCase releaseHold,
            RecordRegularHoldOutboxPort recordOutbox, ReplayRegularHoldOutboxPort replayOutbox, Clock inventoryClock) {
        this.inbox = inbox;
        this.saveInbox = saveInbox;
        this.releaseHold = releaseHold;
        this.recordOutbox = recordOutbox;
        this.replayOutbox = replayOutbox;
        this.clock = inventoryClock;
    }

    @Override
    @Transactional
    public void process(ReleaseRegularHoldMessage message) {
        var replay = inbox.findByCommandId(message.commandId());
        if (replay.isPresent()) {
            assertEquivalentReplay(replay.get(), message);
            replayOutbox.requeue(replay.get().resultEventId(), clock.instant());
            return;
        }
        var sourceReplay = inbox.findBySourcePosition(message.sourceTopic(), message.sourcePartition(),
                message.sourceOffset());
        if (sourceReplay.isPresent()) {
            assertEquivalentReplay(sourceReplay.get(), message);
            replayOutbox.requeue(sourceReplay.get().resultEventId(), clock.instant());
            return;
        }

        RegularStockHoldReleaseResult result = releaseHold.release(message.command());
        RegularHoldFactOutboxEvent outcome = outcome(UUID.randomUUID(), message, result, clock.instant());
        recordOutbox.record(outcome);
        saveInbox.save(new RegularHoldCommandInboxEntry(message.commandId(), "ReleaseRegularStockHold",
                message.command().orderId(), message.command().holdId(), message.command().purchaseRequestId(),
                message.aggregateVersion(), message.payloadFingerprint(), outcome.eventId(), message.sourceTopic(),
                message.sourcePartition(), message.sourceOffset(), clock.instant()));
    }

    private void assertEquivalentReplay(RegularHoldCommandInboxEntry existing, ReleaseRegularHoldMessage message) {
        if (!existing.commandId().equals(message.commandId())
                || !existing.payloadFingerprint().equals(message.payloadFingerprint())
                || !existing.orderId().equals(message.command().orderId())
                || !existing.holdId().equals(message.command().holdId())
                || !existing.purchaseRequestId().equals(message.command().purchaseRequestId())) {
            throw new RegularHoldCommandConflictException("Regular hold release command identity conflicts with inbox history");
        }
    }

    private RegularHoldFactOutboxEvent outcome(UUID eventId, ReleaseRegularHoldMessage message,
            RegularStockHoldReleaseResult result, Instant now) {
        RegularStockHold hold = result.hold();
        String eventType = switch (hold.status()) {
            case RELEASED -> "RegularStockHoldReleased";
            case EXPIRED -> "RegularStockHoldExpired";
            default -> throw new RegularHoldCommandConflictException(
                    "Release command cannot emit a result from hold status " + hold.status());
        };
        UUID causationId = hold.status() == RegularStockHoldStatus.EXPIRED
                ? hold.purchaseRequestId() : message.commandId();
        return new RegularHoldFactOutboxEvent(eventId, eventType, hold.version(), hold.id(),
                hold.purchaseRequestId(), hold.orderId(), causationId, message.traceparent(), message.tracestate(),
                hold.status(), hold.items().stream()
                        .map(item -> new RegularHoldFactItem(item.variantId(), item.quantity())).toList(),
                hold.status() == RegularStockHoldStatus.RELEASED ? message.command().paymentId() : null,
                result.transitioned() ? now : transitionTime(hold), result.reason());
    }

    private Instant transitionTime(RegularStockHold hold) {
        return switch (hold.status()) {
            case RELEASED -> hold.releasedAt();
            case EXPIRED -> hold.expiredAt();
            default -> throw new RegularHoldCommandConflictException("Unsupported terminal regular hold state");
        };
    }
}
