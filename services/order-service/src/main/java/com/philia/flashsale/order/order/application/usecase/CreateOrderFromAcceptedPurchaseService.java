package com.philia.flashsale.order.order.application.usecase;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.application.port.out.PersistOrderCreationPort;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.order.domain.model.OrderLine;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.time.Instant;
import java.util.Objects;

/** Orchestrates Order creation without depending on transport or persistence frameworks. */
public final class CreateOrderFromAcceptedPurchaseService implements CreateOrderFromAcceptedPurchaseUseCase {

    private final PersistOrderCreationPort persistence;
    private final GenerateOrderIdentityPort identities;
    private final GenerateOrderNumberPort orderNumbers;
    private final CurrentTimePort clock;
    private final AcceptedPurchaseFingerprintService fingerprints;

    public CreateOrderFromAcceptedPurchaseService(PersistOrderCreationPort persistence,
            GenerateOrderIdentityPort identities, GenerateOrderNumberPort orderNumbers,
            CurrentTimePort clock, AcceptedPurchaseFingerprintService fingerprints) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.orderNumbers = Objects.requireNonNull(orderNumbers, "orderNumbers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
    }

    @Override
    public OrderCreationResult create(CreateOrderFromAcceptedPurchaseCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = fingerprints.fingerprint(command);
        Instant createdAt = clock.now();
        var orderId = identities.generate();
        var orderNumber = orderNumbers.generate(createdAt, orderId);
        var line = OrderLine.create(identities.generate(), command.variantId(), command.quantity(),
                Money.of(command.unitPrice()));
        var order = Order.create(orderId, orderNumber, command.purchaseRequestId(), command.reservationId(),
                command.campaignId(), command.userId(), command.currency(), line,
                command.acceptedAt(), command.expiresAt());
        var candidate = new OrderCreationCandidate(
                command.eventId(), command.eventType(), command.eventVersion(), command.producer(),
                command.aggregateType(), command.aggregateId(), command.aggregateVersion(), command.correlationId(),
                command.eventId(), createdAt, order, identities.generate(), fingerprint, command.traceparent(),
                command.tracestate(), command.sourceTopic(), command.sourcePartition(), command.sourceOffset(), createdAt);
        return persistence.persist(candidate);
    }
}
