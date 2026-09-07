package com.philia.flashsale.inventory.regularhold.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.inventory.configuration.InventoryRegularHoldProperties;
import com.philia.flashsale.inventory.regularhold.application.command.CreateRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.command.ConfirmRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.command.ReleaseRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.command.RegularStockHoldLine;
import com.philia.flashsale.inventory.regularhold.application.exception.RegularStockHoldApplicationException;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadActiveRegularHoldQuantityPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadRegularStockHoldPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.SaveRegularStockHoldPort;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldItem;
import com.philia.flashsale.inventory.movement.application.port.out.RecordStockMovementPort;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import com.philia.flashsale.inventory.stock.application.port.out.LoadInventoryItemPort;
import com.philia.flashsale.inventory.stock.application.port.out.SaveInventoryItemPort;
import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegularStockHoldApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Test
    void canonicalizesDuplicateVariantsThenCreatesOneAtomicHold() {
        LoadRegularStockHoldPort holds = mock(LoadRegularStockHoldPort.class);
        SaveRegularStockHoldPort save = mock(SaveRegularStockHoldPort.class);
        LoadInventoryItemPort inventory = mock(LoadInventoryItemPort.class);
        LoadActiveRegularHoldQuantityPort active = mock(LoadActiveRegularHoldQuantityPort.class);
        UUID variantA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID variantB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InventoryItem itemA = item(variantA, 8);
        InventoryItem itemB = item(variantB, 8);
        when(inventory.findByVariantIdForUpdate(variantA)).thenReturn(Optional.of(itemA));
        when(inventory.findByVariantIdForUpdate(variantB)).thenReturn(Optional.of(itemB));
        when(active.activeHeldQuantity(any(), any())).thenReturn(0L);
        when(save.save(any(RegularStockHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(holds, save, inventory, active).create(command(List.of(
                new RegularStockHoldLine(variantB, 1), new RegularStockHoldLine(variantA, 1),
                new RegularStockHoldLine(variantA, 2))));

        assertEquals(false, result.replayed());
        assertEquals(List.of(variantA, variantB), result.items().stream().map(item -> item.variantId()).toList());
        assertEquals(List.of(3L, 1L), result.items().stream().map(item -> item.quantity()).toList());
        assertEquals(NOW.plusSeconds(300), result.expiresAt());
    }

    @Test
    void rejectsInsufficientRegularAvailabilityWithoutSavingPartialHold() {
        LoadRegularStockHoldPort holds = mock(LoadRegularStockHoldPort.class);
        SaveRegularStockHoldPort save = mock(SaveRegularStockHoldPort.class);
        LoadInventoryItemPort inventory = mock(LoadInventoryItemPort.class);
        LoadActiveRegularHoldQuantityPort active = mock(LoadActiveRegularHoldQuantityPort.class);
        UUID variant = UUID.randomUUID();
        InventoryItem item = item(variant, 5);
        when(inventory.findByVariantIdForUpdate(variant)).thenReturn(Optional.of(item));
        when(active.activeHeldQuantity(item.id(), NOW)).thenReturn(4L);

        var error = assertThrows(RegularStockHoldApplicationException.class,
                () -> service(holds, save, inventory, active).create(command(List.of(new RegularStockHoldLine(variant, 2)))));

        assertEquals(RegularStockHoldApplicationException.Reason.INSUFFICIENT_STOCK, error.reason());
        verify(save, never()).save(any());
    }

    @Test
    void rejectsRequestedAtOutsideConfiguredNinetySecondSkew() {
        LoadRegularStockHoldPort holds = mock(LoadRegularStockHoldPort.class);
        SaveRegularStockHoldPort save = mock(SaveRegularStockHoldPort.class);
        LoadInventoryItemPort inventory = mock(LoadInventoryItemPort.class);
        LoadActiveRegularHoldQuantityPort active = mock(LoadActiveRegularHoldQuantityPort.class);
        CreateRegularStockHoldCommand command = new CreateRegularStockHoldCommand(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(91),
                List.of(new RegularStockHoldLine(UUID.randomUUID(), 1)));

        var error = assertThrows(RegularStockHoldApplicationException.class,
                () -> service(holds, save, inventory, active).create(command));

        assertEquals(RegularStockHoldApplicationException.Reason.REQUEST_TIME_OUT_OF_RANGE, error.reason());
    }

    @Test
    void confirmsOneHeldLineWithOnePhysicalDeductionAndMovement() {
        LoadRegularStockHoldPort holds = mock(LoadRegularStockHoldPort.class);
        SaveRegularStockHoldPort saveHold = mock(SaveRegularStockHoldPort.class);
        LoadInventoryItemPort inventory = mock(LoadInventoryItemPort.class);
        SaveInventoryItemPort saveInventory = mock(SaveInventoryItemPort.class);
        RecordStockMovementPort recordMovement = mock(RecordStockMovementPort.class);
        LoadActiveRegularHoldQuantityPort active = mock(LoadActiveRegularHoldQuantityPort.class);
        UUID variantId = UUID.randomUUID();
        UUID inventoryId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        RegularStockHold hold = RegularStockHold.held(holdId, purchaseRequestId, orderId, UUID.randomUUID(),
                "a".repeat(64), List.of(new RegularStockHoldItem(inventoryId, variantId, 2, "SKU-1")), NOW,
                java.time.Duration.ofMinutes(5));
        InventoryItem item = new InventoryItem(inventoryId, variantId, "SKU-1", 5, 0, 0, NOW, NOW);
        when(holds.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        when(inventory.findByVariantIdForUpdate(variantId)).thenReturn(Optional.of(item));
        when(saveInventory.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saveHold.save(any(RegularStockHold.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordMovement.record(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(holds, saveHold, inventory, saveInventory, recordMovement, active).confirm(
                new ConfirmRegularStockHoldCommand(UUID.randomUUID(), holdId, purchaseRequestId, orderId,
                        UUID.randomUUID(), NOW.minusSeconds(1)));

        assertEquals(true, result.transitioned());
        assertEquals("CONFIRMED", result.hold().status().name());
        assertEquals(3, item.onHandQuantity());
        verify(recordMovement).record(any(StockMovement.class));
    }

    @Test
    void releasesAHeldHoldWithoutChangingPhysicalStock() {
        LoadRegularStockHoldPort holds = mock(LoadRegularStockHoldPort.class);
        SaveRegularStockHoldPort saveHold = mock(SaveRegularStockHoldPort.class);
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RegularStockHold hold = RegularStockHold.held(holdId, purchaseRequestId, orderId, UUID.randomUUID(),
                "b".repeat(64), List.of(new RegularStockHoldItem(UUID.randomUUID(), UUID.randomUUID(), 1, "SKU-1")),
                NOW, java.time.Duration.ofMinutes(5));
        when(holds.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        when(saveHold.save(any(RegularStockHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(holds, saveHold, mock(LoadInventoryItemPort.class),
                mock(LoadActiveRegularHoldQuantityPort.class)).release(new ReleaseRegularStockHoldCommand(
                        UUID.randomUUID(), holdId, purchaseRequestId, orderId, UUID.randomUUID(),
                        "PAYMENT_DEADLINE_EXPIRED", "CANCELLED"));

        assertEquals(true, result.transitioned());
        assertEquals("RELEASED", result.hold().status().name());
        verify(saveHold).save(hold);
    }

    @Test
    void convertsAReleaseRacingTheInclusiveDeadlineIntoExpiredWithoutStockMovement() {
        LoadRegularStockHoldPort holds = mock(LoadRegularStockHoldPort.class);
        SaveRegularStockHoldPort saveHold = mock(SaveRegularStockHoldPort.class);
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RegularStockHold hold = RegularStockHold.held(holdId, purchaseRequestId, orderId, UUID.randomUUID(),
                "c".repeat(64), List.of(new RegularStockHoldItem(UUID.randomUUID(), UUID.randomUUID(), 1, "SKU-1")),
                NOW.minusSeconds(300), java.time.Duration.ofMinutes(5));
        when(holds.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
        when(saveHold.save(any(RegularStockHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(holds, saveHold, mock(LoadInventoryItemPort.class),
                mock(LoadActiveRegularHoldQuantityPort.class)).release(new ReleaseRegularStockHoldCommand(
                        UUID.randomUUID(), holdId, purchaseRequestId, orderId, UUID.randomUUID(),
                        "PAYMENT_DEADLINE_EXPIRED", "EXPIRED"));

        assertEquals(true, result.transitioned());
        assertEquals("EXPIRED", result.hold().status().name());
        assertEquals("HOLD_TTL_EXPIRED", result.reason());
    }

    private RegularStockHoldApplicationService service(LoadRegularStockHoldPort holds,
            SaveRegularStockHoldPort save, LoadInventoryItemPort inventory,
            LoadActiveRegularHoldQuantityPort active) {
        return service(holds, save, inventory, mock(SaveInventoryItemPort.class), mock(RecordStockMovementPort.class),
                active);
    }

    private RegularStockHoldApplicationService service(LoadRegularStockHoldPort holds,
            SaveRegularStockHoldPort save, LoadInventoryItemPort inventory, SaveInventoryItemPort saveInventory,
            RecordStockMovementPort recordMovement, LoadActiveRegularHoldQuantityPort active) {
        InventoryRegularHoldProperties properties = new InventoryRegularHoldProperties();
        return new RegularStockHoldApplicationService(holds, save, inventory, saveInventory, recordMovement, active, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CreateRegularStockHoldCommand command(List<RegularStockHoldLine> items) {
        return new CreateRegularStockHoldCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW, items);
    }

    private InventoryItem item(UUID variantId, long onHand) {
        return new InventoryItem(UUID.randomUUID(), variantId, "SKU-" + variantId, onHand, 0, 0, NOW, NOW);
    }
}
