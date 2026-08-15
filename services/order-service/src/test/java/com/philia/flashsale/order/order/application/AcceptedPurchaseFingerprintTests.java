package com.philia.flashsale.order.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.usecase.AcceptedPurchaseFingerprintService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcceptedPurchaseFingerprintTests {

    private final AcceptedPurchaseFingerprintService service = new AcceptedPurchaseFingerprintService();

    @Test
    void eventIdentityAndInfrastructurePositionAreExcludedFromBusinessFingerprint() {
        CreateOrderFromAcceptedPurchaseCommand first = command(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("12.5"), 7, "trace-a", 0, 10);
        CreateOrderFromAcceptedPurchaseCommand replay = new CreateOrderFromAcceptedPurchaseCommand(
                UUID.randomUUID(), first.eventType(), first.eventVersion(), first.producer(), first.aggregateType(),
                first.aggregateId(), first.aggregateVersion(), first.correlationId(), first.causationId(),
                first.eventOccurredAt(), first.purchaseRequestId(), first.reservationId(), first.campaignId(),
                first.variantId(), first.userId(), first.quantity(), new BigDecimal("12.5000"), "VND",
                first.acceptedAt(), first.expiresAt(), first.sourceTopic(), 2, 99, "trace-b", "other-state");

        assertThat(service.fingerprint(first)).isEqualTo(service.fingerprint(replay));
    }

    @Test
    void businessFieldsAndCanonicalFieldOrderChangeTheFingerprint() {
        CreateOrderFromAcceptedPurchaseCommand first = command(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("12.5000"), 1, null, 0, 1);
        CreateOrderFromAcceptedPurchaseCommand changedQuantity = command(UUID.randomUUID(), first.purchaseRequestId(),
                new BigDecimal("12.5000"), 2, null, 0, 1);
        CreateOrderFromAcceptedPurchaseCommand changedCurrency = new CreateOrderFromAcceptedPurchaseCommand(
                changedQuantity.eventId(), changedQuantity.eventType(), changedQuantity.eventVersion(),
                changedQuantity.producer(), changedQuantity.aggregateType(), changedQuantity.aggregateId(),
                changedQuantity.aggregateVersion(), changedQuantity.correlationId(), changedQuantity.causationId(),
                changedQuantity.eventOccurredAt(), changedQuantity.purchaseRequestId(), changedQuantity.reservationId(),
                changedQuantity.campaignId(), changedQuantity.variantId(), changedQuantity.userId(),
                changedQuantity.quantity(), changedQuantity.unitPrice(), "USD", changedQuantity.acceptedAt(),
                changedQuantity.expiresAt(), changedQuantity.sourceTopic(), changedQuantity.sourcePartition(),
                changedQuantity.sourceOffset(), changedQuantity.traceparent(), changedQuantity.tracestate());

        assertThat(service.fingerprint(first)).isNotEqualTo(service.fingerprint(changedQuantity));
        assertThat(service.fingerprint(changedQuantity)).isNotEqualTo(service.fingerprint(changedCurrency));
    }

    @Test
    void nonScaleFourAmountIsRejectedInsteadOfRounded() {
        CreateOrderFromAcceptedPurchaseCommand invalid = command(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("12.50001"), 1, null, 0, 1);

        assertThatThrownBy(() -> service.fingerprint(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    private static CreateOrderFromAcceptedPurchaseCommand command(UUID eventId, UUID purchaseRequestId,
            BigDecimal unitPrice, long quantity, String traceparent, int partition, long offset) {
        Instant accepted = Instant.parse("2030-01-01T10:00:00Z");
        return new CreateOrderFromAcceptedPurchaseCommand(eventId, "PurchaseAccepted", 1,
                "flashsale-service", "PURCHASE_REQUEST", purchaseRequestId, 1, UUID.randomUUID(), null,
                accepted, purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                quantity, unitPrice, "vnd", accepted, accepted.plusSeconds(300),
                "flashsale.purchase.events.v1", partition, offset, traceparent, "state");
    }
}
