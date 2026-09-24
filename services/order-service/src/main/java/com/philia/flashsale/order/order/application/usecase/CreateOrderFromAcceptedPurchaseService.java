package com.philia.flashsale.order.order.application.usecase;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.application.port.out.LookupOrderLineNamesPort;
import com.philia.flashsale.order.order.application.port.out.PersistOrderCreationPort;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.order.domain.model.OrderLine;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Orchestrates Order creation without depending on transport or persistence frameworks. */
public final class CreateOrderFromAcceptedPurchaseService implements CreateOrderFromAcceptedPurchaseUseCase {

    private final PersistOrderCreationPort persistence;
    private final GenerateOrderIdentityPort identities;
    private final GenerateOrderNumberPort orderNumbers;
    private final CurrentTimePort clock;
    private final AcceptedPurchaseFingerprintService fingerprints;
    private final LookupOrderLineNamesPort lineNames;

    public CreateOrderFromAcceptedPurchaseService(PersistOrderCreationPort persistence,
            GenerateOrderIdentityPort identities, GenerateOrderNumberPort orderNumbers,
            CurrentTimePort clock, AcceptedPurchaseFingerprintService fingerprints) {
        this(persistence, identities, orderNumbers, clock, fingerprints,
                (variantId, traceId) -> Optional.empty());
    }

    public CreateOrderFromAcceptedPurchaseService(PersistOrderCreationPort persistence,
            GenerateOrderIdentityPort identities, GenerateOrderNumberPort orderNumbers,
            CurrentTimePort clock, AcceptedPurchaseFingerprintService fingerprints,
            LookupOrderLineNamesPort lineNames) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.orderNumbers = Objects.requireNonNull(orderNumbers, "orderNumbers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
        this.lineNames = Objects.requireNonNull(lineNames, "lineNames");
    }

    @Override
    public OrderCreationResult create(CreateOrderFromAcceptedPurchaseCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = fingerprints.fingerprint(command);
        Instant createdAt = clock.now();
        var orderId = identities.generate();
        var orderNumber = orderNumbers.generate(createdAt, orderId);
        var capturedNames = lineNames.lookup(command.variantId(), command.correlationId().toString()).orElse(null);
        var line = OrderLine.create(identities.generate(), command.variantId(), command.quantity(),
                Money.of(command.unitPrice()), capturedNames == null ? null : capturedNames.productName(),
                capturedNames == null ? null : capturedNames.variantName());
        var order = Order.create(orderId, orderNumber, command.purchaseRequestId(), command.reservationId(),
                command.campaignId(), command.userId(), command.currency(), line,
                command.acceptedAt(), command.expiresAt());
        var saga = PurchaseSaga.start(order.id(), command.purchaseRequestId(), command.reservationId(),
                command.expiresAt(), createdAt);
        var candidate = new OrderCreationCandidate(
                command.eventId(), command.eventType(), command.eventVersion(), command.producer(),
                command.aggregateType(), command.aggregateId(), command.aggregateVersion(), command.correlationId(),
                command.eventId(), createdAt, order, identities.generate(), fingerprint, command.traceparent(),
                command.tracestate(), command.sourceTopic(), command.sourcePartition(), command.sourceOffset(), createdAt,
                saga, identities.generate());
        return persistence.persist(candidate);
    }
}
