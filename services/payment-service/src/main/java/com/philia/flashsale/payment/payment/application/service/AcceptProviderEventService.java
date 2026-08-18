package com.philia.flashsale.payment.payment.application.service;

import com.philia.flashsale.payment.payment.application.exception.ProviderReceiptUnavailableException;
import com.philia.flashsale.payment.payment.application.model.webhook.ProviderEventAcceptanceResult;
import com.philia.flashsale.payment.payment.application.model.webhook.VerifiedProviderEvent;
import com.philia.flashsale.payment.payment.application.port.in.AcceptProviderEventUseCase;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentProviderReceiptPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import java.util.Objects;

/** Persists the verified provider receipt before the webhook request is acknowledged. */
public final class AcceptProviderEventService implements AcceptProviderEventUseCase {
    private final PaymentProviderReceiptPort receipts;
    private final PaymentClockPort clock;
    private final PaymentIdentityPort identities;
    private final PaymentTransactionPort transactions;

    public AcceptProviderEventService(PaymentProviderReceiptPort receipts, PaymentClockPort clock,
            PaymentIdentityPort identities, PaymentTransactionPort transactions) {
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public ProviderEventAcceptanceResult accept(VerifiedProviderEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            return transactions.execute(() -> acceptInTransaction(event));
        } catch (ProviderReceiptUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ProviderReceiptUnavailableException(exception);
        }
    }

    private ProviderEventAcceptanceResult acceptInTransaction(VerifiedProviderEvent event) {
        var existing = receipts.findByProviderEventId(event.providerEventId());
        if (existing.isPresent()) {
            PaymentEventStatus status = PaymentEventStatus.from(existing.get().processingStatus());
            return new ProviderEventAcceptanceResult(
                    status == PaymentEventStatus.IGNORED
                            ? ProviderEventAcceptanceResult.Status.IGNORED
                            : ProviderEventAcceptanceResult.Status.DUPLICATE,
                    existing.get().id());
        }
        var receipt = new PaymentProviderReceiptPort.Receipt(
                identities.newId(), event.providerEventId(), event.providerEventType(),
                event.liveMode(), event.providerApiVersion(), event.providerObjectId(), event.paymentId(),
                event.attemptId(), event.orderId(), event.providerCreatedAt(), event.verifiedAt(),
                event.supported() ? "PENDING" : "IGNORED", null, 0, event.verifiedAt());
        var stored = receipts.receive(receipt);
        return new ProviderEventAcceptanceResult(
                event.supported() ? ProviderEventAcceptanceResult.Status.ACCEPTED
                        : ProviderEventAcceptanceResult.Status.IGNORED,
                stored.id());
    }

    private enum PaymentEventStatus {
        IGNORED,
        OTHER;

        static PaymentEventStatus from(String status) {
            return "IGNORED".equals(status) ? IGNORED : OTHER;
        }
    }
}
