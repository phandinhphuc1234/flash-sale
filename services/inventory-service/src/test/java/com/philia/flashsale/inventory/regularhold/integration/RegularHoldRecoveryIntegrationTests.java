package com.philia.flashsale.inventory.regularhold.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.inventory.configuration.InventoryRegularHoldProperties;
import com.philia.flashsale.inventory.movement.application.port.out.RecordStockMovementPort;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import com.philia.flashsale.inventory.regularhold.application.command.ConfirmRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.command.CreateRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.command.RegularStockHoldLine;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadActiveRegularHoldQuantityPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadRegularStockHoldPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.SaveRegularStockHoldPort;
import com.philia.flashsale.inventory.regularhold.application.usecase.RegularStockHoldApplicationService;
import com.philia.flashsale.inventory.regularhold.domain.exception.RegularStockHoldDomainException;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldItem;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldStatus;
import com.philia.flashsale.inventory.stock.application.port.out.LoadInventoryItemPort;
import com.philia.flashsale.inventory.stock.application.port.out.SaveInventoryItemPort;
import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Characterizes Inventory recovery boundaries before the release command and expiry worker are
 * introduced by T081-T083. These tests deliberately exercise the aggregate/application boundary,
 * not the database with direct business-row writes.
 */
class RegularHoldRecoveryIntegrationTests {
    private static final Instant CREATED_AT = Instant.parse("2026-09-07T00:00:00Z");
    private static final Instant EXPIRY = CREATED_AT.plus(Duration.ofMinutes(5));

    @Test
    void expiryDeadlineIsInclusiveAndLateConfirmDoesNotMoveStock() {
        Fixture fixture = fixture(EXPIRY);

        var result = fixture.service.confirm(fixture.confirmCommand());

        assertThat(result.hold().status()).isEqualTo(RegularStockHoldStatus.EXPIRED);
        assertThat(result.transitioned()).isTrue();
        assertThat(result.hold().expiredAt()).isEqualTo(EXPIRY);
        assertThatThrownBy(() -> fixture.hold.release(EXPIRY))
                .isInstanceOf(RegularStockHoldDomainException.class);
        verify(fixture.saveHold).save(fixture.hold);
        verify(fixture.saveInventory, never()).save(any());
        verify(fixture.movements, never()).record(any());

        var replay = fixture.service.confirm(fixture.confirmCommand());
        assertThat(replay.hold().status()).isEqualTo(RegularStockHoldStatus.EXPIRED);
        assertThat(replay.transitioned()).isFalse();
        verify(fixture.movements, never()).record(any());
    }

    @Test
    void confirmationJustBeforeDeadlineDeductsOnceAndReplayIsCurrentState() {
        Fixture fixture = fixture(EXPIRY.minusSeconds(1));

        var first = fixture.service.confirm(fixture.confirmCommand());
        var replay = fixture.service.confirm(fixture.confirmCommand());

        assertThat(first.hold().status()).isEqualTo(RegularStockHoldStatus.CONFIRMED);
        assertThat(first.transitioned()).isTrue();
        assertThat(replay.hold().status()).isEqualTo(RegularStockHoldStatus.CONFIRMED);
        assertThat(replay.transitioned()).isFalse();
        assertThat(fixture.inventory.onHandQuantity()).isEqualTo(4);
        verify(fixture.saveInventory).save(fixture.inventory);
        verify(fixture.movements).record(any(StockMovement.class));
    }

    @Test
    void releaseAndConfirmCannotBothBecomeTerminalTransitions() {
        RegularStockHold released = held();
        released.release(CREATED_AT.plusSeconds(1));
        assertThat(released.status()).isEqualTo(RegularStockHoldStatus.RELEASED);
        assertThatThrownBy(() -> released.confirm(CREATED_AT.plusSeconds(2)))
                .isInstanceOf(RegularStockHoldDomainException.class);

        RegularStockHold confirmed = held();
        confirmed.confirm(CREATED_AT.plusSeconds(1));
        assertThat(confirmed.status()).isEqualTo(RegularStockHoldStatus.CONFIRMED);
        assertThatThrownBy(() -> confirmed.release(CREATED_AT.plusSeconds(2)))
                .isInstanceOf(RegularStockHoldDomainException.class);
    }

    @Test
    void lateConfirmAfterReleaseReturnsReleasedCurrentStateWithoutMovement() {
        Fixture fixture = fixture(CREATED_AT.plusSeconds(2));
        fixture.hold.release(CREATED_AT.plusSeconds(1));

        var result = fixture.service.confirm(fixture.confirmCommand());

        assertThat(result.hold().status()).isEqualTo(RegularStockHoldStatus.RELEASED);
        assertThat(result.transitioned()).isFalse();
        verify(fixture.saveInventory, never()).save(any());
        verify(fixture.movements, never()).record(any());
    }

    @Test
    void equivalentCreateReplayReturnsTheOriginalHoldWithoutAnotherSave() {
        UUID purchaseRequestId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID shopperId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        CreateRegularStockHoldCommand command = new CreateRegularStockHoldCommand(holdId, purchaseRequestId,
                orderId, shopperId, CREATED_AT, List.of(new RegularStockHoldLine(variantId, 1)));
        LoadRegularStockHoldPort holds = org.mockito.Mockito.mock(LoadRegularStockHoldPort.class);
        SaveRegularStockHoldPort save = org.mockito.Mockito.mock(SaveRegularStockHoldPort.class);
        LoadInventoryItemPort inventory = org.mockito.Mockito.mock(LoadInventoryItemPort.class);
        LoadActiveRegularHoldQuantityPort active = org.mockito.Mockito.mock(LoadActiveRegularHoldQuantityPort.class);
        RegularStockHold[] saved = new RegularStockHold[1];
        when(holds.findByPurchaseRequestId(purchaseRequestId)).thenAnswer(invocation ->
                saved[0] == null ? Optional.empty() : Optional.of(saved[0]));
        when(holds.findById(holdId)).thenReturn(Optional.empty());
        when(holds.findByOrderId(orderId)).thenReturn(Optional.empty());
        InventoryItem item = new InventoryItem(UUID.randomUUID(), variantId, "SKU-REPLAY", 3, 0, 0,
                CREATED_AT, CREATED_AT);
        when(inventory.findByVariantIdForUpdate(variantId)).thenReturn(Optional.of(item));
        when(active.activeHeldQuantity(item.id(), CREATED_AT)).thenReturn(0L);
        when(save.save(any(RegularStockHold.class))).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });
        RegularStockHoldApplicationService service = new RegularStockHoldApplicationService(holds, save, inventory,
                org.mockito.Mockito.mock(SaveInventoryItemPort.class), org.mockito.Mockito.mock(RecordStockMovementPort.class),
                active, new InventoryRegularHoldProperties(), Clock.fixed(CREATED_AT, ZoneOffset.UTC));

        var first = service.create(command);
        var replay = service.create(command);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.holdId()).isEqualTo(holdId);
        verify(save).save(any(RegularStockHold.class));
    }

    private Fixture fixture(Instant now) {
        LoadRegularStockHoldPort holds = org.mockito.Mockito.mock(LoadRegularStockHoldPort.class);
        SaveRegularStockHoldPort saveHold = org.mockito.Mockito.mock(SaveRegularStockHoldPort.class);
        LoadInventoryItemPort inventoryPort = org.mockito.Mockito.mock(LoadInventoryItemPort.class);
        SaveInventoryItemPort saveInventory = org.mockito.Mockito.mock(SaveInventoryItemPort.class);
        RecordStockMovementPort movements = org.mockito.Mockito.mock(RecordStockMovementPort.class);
        LoadActiveRegularHoldQuantityPort active = org.mockito.Mockito.mock(LoadActiveRegularHoldQuantityPort.class);
        RegularStockHold hold = held();
        InventoryItem inventory = new InventoryItem(hold.items().get(0).inventoryItemId(), hold.items().get(0).variantId(),
                "SKU-RECOVERY", 5, 0, 0, CREATED_AT, CREATED_AT);
        when(holds.findByIdForUpdate(hold.id())).thenReturn(Optional.of(hold));
        when(inventoryPort.findByVariantIdForUpdate(hold.items().get(0).variantId())).thenReturn(Optional.of(inventory));
        when(saveInventory.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saveHold.save(any(RegularStockHold.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RegularStockHoldApplicationService service = new RegularStockHoldApplicationService(holds, saveHold,
                inventoryPort, saveInventory, movements, active, new InventoryRegularHoldProperties(),
                Clock.fixed(now, ZoneOffset.UTC));
        return new Fixture(service, hold, inventory, saveHold, saveInventory, movements);
    }

    private RegularStockHold held() {
        return RegularStockHold.held(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "a".repeat(64), List.of(new RegularStockHoldItem(UUID.randomUUID(), UUID.randomUUID(), 1, "SKU-1")),
                CREATED_AT, Duration.ofMinutes(5));
    }

    private record Fixture(RegularStockHoldApplicationService service, RegularStockHold hold,
            InventoryItem inventory, SaveRegularStockHoldPort saveHold, SaveInventoryItemPort saveInventory,
            RecordStockMovementPort movements) {
        ConfirmRegularStockHoldCommand confirmCommand() {
            return new ConfirmRegularStockHoldCommand(UUID.randomUUID(), hold.id(), hold.purchaseRequestId(),
                    hold.orderId(), UUID.randomUUID(), CREATED_AT);
        }
    }
}
