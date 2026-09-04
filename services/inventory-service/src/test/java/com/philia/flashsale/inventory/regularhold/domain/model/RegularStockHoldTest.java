package com.philia.flashsale.inventory.regularhold.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.inventory.regularhold.domain.exception.RegularStockHoldDomainException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegularStockHoldTest {
    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Test
    void createsExactlyFiveMinuteHeldAggregateWithCanonicalItems() {
        UUID variantA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID variantB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        RegularStockHold hold = RegularStockHold.held(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "a".repeat(64), List.of(
                        new RegularStockHoldItem(UUID.randomUUID(), variantB, 1, "SKU-B"),
                        new RegularStockHoldItem(UUID.randomUUID(), variantA, 2, "SKU-A")), NOW, Duration.ofMinutes(5));

        assertEquals(RegularStockHoldStatus.HELD, hold.status());
        assertEquals(NOW.plus(Duration.ofMinutes(5)), hold.expiresAt());
        assertEquals(List.of(variantA, variantB), hold.items().stream().map(RegularStockHoldItem::variantId).toList());
    }

    @Test
    void rejectsNonFiveMinuteTtlAndDuplicateVariants() {
        UUID variant = UUID.randomUUID();
        assertThrows(RegularStockHoldDomainException.class, () -> RegularStockHold.held(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "b".repeat(64),
                List.of(new RegularStockHoldItem(UUID.randomUUID(), variant, 1, "SKU-1")), NOW, Duration.ofMinutes(4)));
        assertThrows(RegularStockHoldDomainException.class, () -> new RegularStockHold(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "c".repeat(64),
                RegularStockHoldStatus.HELD, NOW.plus(Duration.ofMinutes(5)), null, null, null, NOW, NOW, 0,
                List.of(new RegularStockHoldItem(UUID.randomUUID(), variant, 1, "SKU-1"),
                        new RegularStockHoldItem(UUID.randomUUID(), variant, 1, "SKU-2"))));
    }

    @Test
    void comparesTheCompleteReplayIdentity() {
        UUID holdId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID shopperId = UUID.randomUUID();
        RegularStockHold hold = RegularStockHold.held(holdId, UUID.randomUUID(), orderId, shopperId,
                "d".repeat(64), List.of(new RegularStockHoldItem(UUID.randomUUID(), UUID.randomUUID(), 1, "SKU-1")),
                NOW, Duration.ofMinutes(5));

        assertTrue(hold.hasEquivalentIdentity(holdId, orderId, shopperId, "d".repeat(64)));
        assertFalse(hold.hasEquivalentIdentity(holdId, orderId, shopperId, "e".repeat(64)));
    }

    @Test
    void confirmsOnlyOnceBeforeTheExactExpiryBoundary() {
        RegularStockHold hold = held();

        hold.confirm(NOW.plusSeconds(1));

        assertEquals(RegularStockHoldStatus.CONFIRMED, hold.status());
        assertEquals(NOW.plusSeconds(1), hold.confirmedAt());
        assertEquals(1, hold.version());
        assertThrows(RegularStockHoldDomainException.class, () -> hold.release(NOW.plusSeconds(2)));
    }

    @Test
    void expiresHeldReservationAndRejectsLateConfirmation() {
        RegularStockHold hold = held();

        assertThrows(RegularStockHoldDomainException.class,
                () -> hold.confirm(NOW.plus(Duration.ofMinutes(5))));
        hold.expire(NOW.plus(Duration.ofMinutes(5)));

        assertEquals(RegularStockHoldStatus.EXPIRED, hold.status());
        assertEquals(NOW.plus(Duration.ofMinutes(5)), hold.expiredAt());
    }

    private RegularStockHold held() {
        return RegularStockHold.held(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "f".repeat(64), List.of(new RegularStockHoldItem(UUID.randomUUID(), UUID.randomUUID(), 1, "SKU-1")),
                NOW, Duration.ofMinutes(5));
    }
}
