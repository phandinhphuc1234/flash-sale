package com.philia.flashsale.inventory.regularhold.application.usecase;

import com.philia.flashsale.inventory.configuration.InventoryRegularHoldProperties;
import com.philia.flashsale.inventory.regularhold.application.command.CreateRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.command.ConfirmRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.command.RegularStockHoldLine;
import com.philia.flashsale.inventory.regularhold.application.exception.RegularStockHoldApplicationException;
import com.philia.flashsale.inventory.regularhold.application.port.in.ConfirmRegularStockHoldUseCase;
import com.philia.flashsale.inventory.regularhold.application.port.in.CreateRegularStockHoldUseCase;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadActiveRegularHoldQuantityPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadRegularStockHoldPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.SaveRegularStockHoldPort;
import com.philia.flashsale.inventory.regularhold.application.result.RegularStockHoldResult;
import com.philia.flashsale.inventory.regularhold.application.result.RegularStockHoldConfirmationResult;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldItem;
import com.philia.flashsale.inventory.movement.application.port.out.RecordStockMovementPort;
import com.philia.flashsale.inventory.movement.domain.model.MovementType;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import com.philia.flashsale.inventory.stock.application.port.out.LoadInventoryItemPort;
import com.philia.flashsale.inventory.stock.application.port.out.SaveInventoryItemPort;
import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates one locked, all-or-nothing regular hold without changing physical stock. */
@Service
public class RegularStockHoldApplicationService implements CreateRegularStockHoldUseCase, ConfirmRegularStockHoldUseCase {
    private static final long MAX_QUANTITY_PER_VARIANT = 10;

    private final LoadRegularStockHoldPort holds;
    private final SaveRegularStockHoldPort saveHold;
    private final LoadInventoryItemPort inventoryItems;
    private final SaveInventoryItemPort saveInventoryItem;
    private final RecordStockMovementPort recordMovement;
    private final LoadActiveRegularHoldQuantityPort activeHeldQuantities;
    private final InventoryRegularHoldProperties properties;
    private final Clock clock;

    public RegularStockHoldApplicationService(
            LoadRegularStockHoldPort holds,
            SaveRegularStockHoldPort saveHold,
            LoadInventoryItemPort inventoryItems,
            SaveInventoryItemPort saveInventoryItem,
            RecordStockMovementPort recordMovement,
            LoadActiveRegularHoldQuantityPort activeHeldQuantities,
            InventoryRegularHoldProperties properties,
            Clock inventoryClock) {
        this.holds = holds;
        this.saveHold = saveHold;
        this.inventoryItems = inventoryItems;
        this.saveInventoryItem = saveInventoryItem;
        this.recordMovement = recordMovement;
        this.activeHeldQuantities = activeHeldQuantities;
        this.properties = properties;
        this.clock = inventoryClock;
    }

    @Override
    @Transactional
    public RegularStockHoldResult create(CreateRegularStockHoldCommand command) {
        Instant now = clock.instant();
        validateRequestTime(command.requestedAt(), now);
        Map<UUID, Long> canonicalItems = canonicalize(command.items());
        String fingerprint = fingerprint(canonicalItems);

        var existing = holds.findByPurchaseRequestId(command.purchaseRequestId());
        if (existing.isPresent()) {
            return compatibleReplay(command, fingerprint, existing.get());
        }
        assertNoIdentityCollision(command);

        List<RegularStockHoldItem> holdItems = new ArrayList<>(canonicalItems.size());
        for (Map.Entry<UUID, Long> requested : canonicalItems.entrySet()) {
            InventoryItem item = inventoryItems.findByVariantIdForUpdate(requested.getKey())
                    .orElseThrow(RegularStockHoldApplicationException::inventoryItemNotFound);
            long activeHeld = activeHeldQuantities.activeHeldQuantity(item.id(), now);
            long available = item.availableQuantity() - activeHeld;
            if (requested.getValue() > available) {
                throw RegularStockHoldApplicationException.insufficientStock();
            }
            holdItems.add(new RegularStockHoldItem(item.id(), item.variantId(), requested.getValue(), item.skuSnapshot()));
        }

        // A competing same-key request may have committed while this transaction waited on the first
        // deterministically ordered Inventory row. Recheck the durable idempotency identity under lock.
        existing = holds.findByPurchaseRequestId(command.purchaseRequestId());
        if (existing.isPresent()) {
            return compatibleReplay(command, fingerprint, existing.get());
        }
        assertNoIdentityCollision(command);

        RegularStockHold created = RegularStockHold.held(command.holdId(), command.purchaseRequestId(),
                command.orderId(), command.shopperId(), fingerprint, holdItems, now, properties.getTtl());
        return RegularStockHoldResult.from(saveHold.save(created), false);
    }

    /**
     * Confirm a payment-backed hold under a hold lock followed by deterministic Inventory-row locks.
     * All physical deductions, audit movements, and the terminal hold state share this transaction.
     */
    @Override
    @Transactional
    public RegularStockHoldConfirmationResult confirm(ConfirmRegularStockHoldCommand command) {
        Instant now = clock.instant();
        RegularStockHold hold = holds.findByIdForUpdate(command.holdId())
                .orElseThrow(() -> new IllegalArgumentException("Regular stock hold is not found"));
        assertConfirmationIdentity(command, hold);

        if (!hold.isHeld()) {
            return new RegularStockHoldConfirmationResult(hold, false);
        }
        if (hold.isExpiredAt(now)) {
            hold.expire(now);
            return new RegularStockHoldConfirmationResult(saveHold.save(hold), true);
        }

        for (RegularStockHoldItem item : hold.items()) {
            InventoryItem inventoryItem = inventoryItems.findByVariantIdForUpdate(item.variantId())
                    .orElseThrow(RegularStockHoldApplicationException::inventoryItemNotFound);
            inventoryItem.decreaseStock(item.quantity(), now);
            InventoryItem persisted = saveInventoryItem.save(inventoryItem);
            recordMovement.record(new StockMovement(UUID.randomUUID(), movementRequestId(command.commandId(), item),
                    persisted.id(), null, "REGULAR_STOCK_HOLD", hold.id(), MovementType.REGULAR_HOLD_CONFIRMED,
                    -item.quantity(), 0, persisted.onHandQuantity(), persisted.campaignAllocatedQuantity(),
                    "PAID_CONFIRMATION", now));
        }
        hold.confirm(now);
        return new RegularStockHoldConfirmationResult(saveHold.save(hold), true);
    }

    private void validateRequestTime(Instant requestedAt, Instant now) {
        Duration skew = Duration.between(requestedAt, now).abs();
        if (skew.compareTo(properties.getMaxClockSkew()) > 0) {
            throw RegularStockHoldApplicationException.requestTimeOutOfRange();
        }
    }

    private Map<UUID, Long> canonicalize(List<RegularStockHoldLine> lines) {
        Map<UUID, Long> canonical = new TreeMap<>();
        for (RegularStockHoldLine line : lines) {
            canonical.merge(line.variantId(), line.quantity(), Math::addExact);
        }
        if (canonical.size() > 20 || canonical.values().stream().anyMatch(quantity -> quantity > MAX_QUANTITY_PER_VARIANT)) {
            throw new IllegalArgumentException("Regular hold supports at most twenty variants and quantity ten per variant");
        }
        return canonical;
    }

    private RegularStockHoldResult compatibleReplay(
            CreateRegularStockHoldCommand command, String fingerprint, RegularStockHold existing) {
        if (!existing.hasEquivalentIdentity(command.holdId(), command.orderId(), command.shopperId(), fingerprint)) {
            throw RegularStockHoldApplicationException.identityConflict();
        }
        return RegularStockHoldResult.from(existing, true);
    }

    private void assertNoIdentityCollision(CreateRegularStockHoldCommand command) {
        if (holds.findById(command.holdId()).isPresent() || holds.findByOrderId(command.orderId()).isPresent()) {
            throw RegularStockHoldApplicationException.identityConflict();
        }
    }

    private void assertConfirmationIdentity(ConfirmRegularStockHoldCommand command, RegularStockHold hold) {
        if (!hold.orderId().equals(command.orderId()) || !hold.purchaseRequestId().equals(command.purchaseRequestId())) {
            throw RegularStockHoldApplicationException.identityConflict();
        }
    }

    private UUID movementRequestId(UUID commandId, RegularStockHoldItem item) {
        return UUID.nameUUIDFromBytes((commandId + ":" + item.inventoryItemId()).getBytes(StandardCharsets.UTF_8));
    }

    private String fingerprint(Map<UUID, Long> canonicalItems) {
        String canonical = canonicalItems.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce("", (left, right) -> left + right + "\n");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
