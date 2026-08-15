package com.philia.flashsale.order.order.adapter.out.identity;

import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/** Service-local identity, clock, and collision-safe Order-number adapter. */
public class OrderIdentityAdapter implements GenerateOrderIdentityPort, GenerateOrderNumberPort, CurrentTimePort {

    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);
    private final Clock clock;

    public OrderIdentityAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }

    @Override
    public String generate(Instant createdAt, UUID orderId) {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(orderId, "orderId");
        // The full UUID suffix makes the display number collision-safe without a database round-trip.
        return "FS-" + ORDER_DATE.format(createdAt) + "-" + orderId.toString().toUpperCase();
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
