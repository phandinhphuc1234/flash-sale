package com.philia.flashsale.inventory.regularhold.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.inventory.regularhold.application.command.ConfirmRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.exception.RegularHoldCommandConflictException;
import com.philia.flashsale.inventory.regularhold.application.model.ConfirmRegularHoldMessage;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldCommandInboxEntry;
import com.philia.flashsale.inventory.regularhold.application.port.in.ConfirmRegularStockHoldUseCase;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadRegularHoldCommandInboxPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.RecordRegularHoldOutboxPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.ReplayRegularHoldOutboxPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.SaveRegularHoldCommandInboxPort;
import com.philia.flashsale.inventory.regularhold.application.result.RegularStockHoldConfirmationResult;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldItem;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegularHoldCommandProcessingServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T13:31:00Z");

    @Test
    void commitsOneInboxReceiptAndStableConfirmedFactForAFirstDelivery() {
        LoadRegularHoldCommandInboxPort inbox = mock(LoadRegularHoldCommandInboxPort.class);
        SaveRegularHoldCommandInboxPort saveInbox = mock(SaveRegularHoldCommandInboxPort.class);
        ConfirmRegularStockHoldUseCase confirm = mock(ConfirmRegularStockHoldUseCase.class);
        RecordRegularHoldOutboxPort outbox = mock(RecordRegularHoldOutboxPort.class);
        ConfirmRegularHoldMessage message = message();
        when(inbox.findByCommandId(message.commandId())).thenReturn(Optional.empty());
        when(inbox.findBySourcePosition(any(), any(Integer.class), any(Long.class))).thenReturn(Optional.empty());
        when(confirm.confirm(message.command())).thenReturn(new RegularStockHoldConfirmationResult(confirmedHold(message), true));

        service(inbox, saveInbox, confirm, outbox, mock(ReplayRegularHoldOutboxPort.class)).process(message);

        verify(confirm).confirm(message.command());
        verify(outbox).record(any());
        verify(saveInbox).save(any(RegularHoldCommandInboxEntry.class));
    }

    @Test
    void exactCommandReplayDoesNotConfirmOrPublishAgain() {
        LoadRegularHoldCommandInboxPort inbox = mock(LoadRegularHoldCommandInboxPort.class);
        SaveRegularHoldCommandInboxPort saveInbox = mock(SaveRegularHoldCommandInboxPort.class);
        ConfirmRegularStockHoldUseCase confirm = mock(ConfirmRegularStockHoldUseCase.class);
        RecordRegularHoldOutboxPort outbox = mock(RecordRegularHoldOutboxPort.class);
        ReplayRegularHoldOutboxPort replayOutbox = mock(ReplayRegularHoldOutboxPort.class);
        ConfirmRegularHoldMessage message = message();
        when(inbox.findByCommandId(message.commandId())).thenReturn(Optional.of(inboxEntry(message)));

        service(inbox, saveInbox, confirm, outbox, replayOutbox).process(message);

        verify(confirm, never()).confirm(any());
        verify(outbox, never()).record(any());
        verify(saveInbox, never()).save(any());
        verify(replayOutbox).requeue(any(), any());
    }

    @Test
    void conflictsWhenASeenCommandIdCarriesDifferentDurableIdentity() {
        LoadRegularHoldCommandInboxPort inbox = mock(LoadRegularHoldCommandInboxPort.class);
        ConfirmRegularHoldMessage message = message();
        RegularHoldCommandInboxEntry conflict = new RegularHoldCommandInboxEntry(message.commandId(),
                "ConfirmRegularStockHold", UUID.randomUUID(), message.command().holdId(),
                message.command().purchaseRequestId(), message.aggregateVersion(), message.payloadFingerprint(),
                UUID.randomUUID(), message.sourceTopic(), message.sourcePartition(), message.sourceOffset(), NOW);
        when(inbox.findByCommandId(message.commandId())).thenReturn(Optional.of(conflict));

        assertThatThrownBy(() -> service(inbox, mock(SaveRegularHoldCommandInboxPort.class),
                mock(ConfirmRegularStockHoldUseCase.class), mock(RecordRegularHoldOutboxPort.class),
                mock(ReplayRegularHoldOutboxPort.class)).process(message))
                .isInstanceOf(RegularHoldCommandConflictException.class);
    }

    private static RegularHoldCommandProcessingService service(LoadRegularHoldCommandInboxPort inbox,
            SaveRegularHoldCommandInboxPort saveInbox, ConfirmRegularStockHoldUseCase confirm,
            RecordRegularHoldOutboxPort outbox, ReplayRegularHoldOutboxPort replayOutbox) {
        return new RegularHoldCommandProcessingService(inbox, saveInbox, confirm, outbox, replayOutbox,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ConfirmRegularHoldMessage message() {
        UUID commandId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        return new ConfirmRegularHoldMessage(new ConfirmRegularStockHoldCommand(commandId, holdId,
                purchaseRequestId, orderId, UUID.randomUUID(), NOW), 1L,
                "d3d2af24bb08614358d9f66d5f4242778858c4b619a9de908b370c676165ee10", "commands", 0, 1L,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", null);
    }

    private static RegularHoldCommandInboxEntry inboxEntry(ConfirmRegularHoldMessage message) {
        return new RegularHoldCommandInboxEntry(message.commandId(), "ConfirmRegularStockHold",
                message.command().orderId(), message.command().holdId(), message.command().purchaseRequestId(),
                message.aggregateVersion(), message.payloadFingerprint(), UUID.randomUUID(), message.sourceTopic(),
                message.sourcePartition(), message.sourceOffset(), NOW);
    }

    private static RegularStockHold confirmedHold(ConfirmRegularHoldMessage message) {
        return new RegularStockHold(message.command().holdId(), message.command().purchaseRequestId(),
                message.command().orderId(), UUID.randomUUID(),
                "d3d2af24bb08614358d9f66d5f4242778858c4b619a9de908b370c676165ee10",
                com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldStatus.CONFIRMED,
                NOW.plusSeconds(300), NOW, null, null, NOW.minusSeconds(1), NOW, 1L,
                List.of(new RegularStockHoldItem(UUID.randomUUID(), UUID.randomUUID(), 1L, "SKU-1")));
    }
}
