package com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa;

import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity.RegularStockHoldItemJpaEntity;
import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity.RegularStockHoldJpaEntity;
import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.repository.RegularStockHoldItemJpaRepository;
import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.repository.RegularStockHoldJpaRepository;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadActiveRegularHoldQuantityPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadRegularStockHoldPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.SaveRegularStockHoldPort;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** JPA boundary for durable hold identity, items, and active-held availability accounting. */
@Component
public class RegularStockHoldPersistenceAdapter implements LoadRegularStockHoldPort,
        SaveRegularStockHoldPort, LoadActiveRegularHoldQuantityPort {
    private final RegularStockHoldJpaRepository holds;
    private final RegularStockHoldItemJpaRepository items;

    public RegularStockHoldPersistenceAdapter(RegularStockHoldJpaRepository holds,
            RegularStockHoldItemJpaRepository items) {
        this.holds = holds;
        this.items = items;
    }

    @Override
    public Optional<RegularStockHold> findByPurchaseRequestId(UUID purchaseRequestId) {
        return holds.findByPurchaseRequestId(purchaseRequestId).map(this::toDomain);
    }

    @Override
    public Optional<RegularStockHold> findById(UUID holdId) {
        return holds.findById(holdId).map(this::toDomain);
    }

    @Override
    public Optional<RegularStockHold> findByIdForUpdate(UUID holdId) {
        return holds.findWithLockById(holdId).map(this::toDomain);
    }

    @Override
    public Optional<RegularStockHold> findByOrderId(UUID orderId) {
        return holds.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public RegularStockHold save(RegularStockHold hold) {
        var existing = holds.findById(hold.id());
        if (existing.isPresent()) {
            RegularStockHoldJpaEntity entity = existing.get();
            entity.applyMutableState(hold.status(), hold.confirmedAt(), hold.releasedAt(), hold.expiredAt(),
                    hold.updatedAt());
            return toDomain(holds.saveAndFlush(entity));
        }

        RegularStockHoldJpaEntity entity = new RegularStockHoldJpaEntity(hold.id(),
                hold.purchaseRequestId(), hold.orderId(), hold.shopperId(), hold.requestFingerprint(),
                hold.status(), hold.expiresAt(), hold.confirmedAt(), hold.releasedAt(), hold.expiredAt(),
                hold.version(), hold.createdAt(), hold.updatedAt());
        holds.saveAndFlush(entity);
        items.saveAll(hold.items().stream().map(item -> new RegularStockHoldItemJpaEntity(
                UUID.randomUUID(), hold.id(), item.inventoryItemId(), item.variantId(), item.quantity(),
                item.skuSnapshot(), hold.createdAt())).toList());
        return toDomain(entity);
    }

    @Override
    public long activeHeldQuantity(UUID inventoryItemId, Instant at) {
        return items.sumActiveHeldQuantity(inventoryItemId, at);
    }

    private RegularStockHold toDomain(RegularStockHoldJpaEntity entity) {
        List<RegularStockHoldItem> holdItems = items.findByHoldIdOrderByVariantIdAsc(entity.getId()).stream()
                .map(item -> new RegularStockHoldItem(item.getInventoryItemId(), item.getVariantId(),
                        item.getQuantity(), item.getSkuSnapshot()))
                .toList();
        return new RegularStockHold(entity.getId(), entity.getPurchaseRequestId(), entity.getOrderId(),
                entity.getShopperId(), entity.getRequestFingerprint(), entity.getStatus(), entity.getExpiresAt(),
                entity.getConfirmedAt(), entity.getReleasedAt(), entity.getExpiredAt(), entity.getCreatedAt(),
                entity.getUpdatedAt(), entity.getVersion(), holdItems);
    }
}
