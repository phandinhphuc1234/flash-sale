package com.philia.flashsale.payment.payment.application.service;

import com.philia.flashsale.payment.payment.application.exception.InvalidPaymentRequestException;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestResult;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestResult.ConflictDetails;
import com.philia.flashsale.payment.payment.application.port.in.AcceptPaymentRequestUseCase;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentCommandInboxPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Atomically accepts the Order-owned PaymentRequested command.
 *
 * <p>This use case deliberately has no provider port: acceptance creates durable Payment truth
 * only. Stripe interaction starts in the later Checkout feature after this transaction commits.
 */
public final class AcceptPaymentRequestService implements AcceptPaymentRequestUseCase {

    public static final String EVENT_TYPE = "PaymentRequested";
    public static final int EVENT_VERSION = 1;
    public static final String PRODUCER = "order-service";
    public static final String AGGREGATE_TYPE = "ORDER";
    public static final String PAYMENT_FAILURE_EVENT_TYPE = "PaymentFailed";
    public static final String PAYMENT_EVENT_TOPIC = "flashsale.payment.events.v1";

    private final PaymentCommandInboxPort inbox;
    private final LoadPaymentPort payments;
    private final SavePaymentPort paymentWriter;
    private final SavePaymentOutboxPort outbox;
    private final PaymentClockPort clock;
    private final PaymentIdentityPort identities;
    private final PaymentTransactionPort transactions;
    private final String paymentEventTopic;

    public AcceptPaymentRequestService(PaymentCommandInboxPort inbox, LoadPaymentPort payments,
            SavePaymentPort paymentWriter, SavePaymentOutboxPort outbox, PaymentClockPort clock,
            PaymentIdentityPort identities, PaymentTransactionPort transactions) {
        this(inbox, payments, paymentWriter, outbox, clock, identities, transactions, PAYMENT_EVENT_TOPIC);
    }

    public AcceptPaymentRequestService(PaymentCommandInboxPort inbox, LoadPaymentPort payments,
            SavePaymentPort paymentWriter, SavePaymentOutboxPort outbox, PaymentClockPort clock,
            PaymentIdentityPort identities, PaymentTransactionPort transactions, String paymentEventTopic) {
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.paymentWriter = Objects.requireNonNull(paymentWriter, "paymentWriter");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        if (paymentEventTopic == null || paymentEventTopic.isBlank()) {
            throw new IllegalArgumentException("payment event topic must not be blank");
        }
        this.paymentEventTopic = paymentEventTopic;
    }

    @Override
    public AcceptPaymentRequestResult accept(AcceptPaymentRequestCommand command) {
        Objects.requireNonNull(command, "command");
        validateEnvelope(command);
        String fingerprint = fingerprint(command);
        return transactions.execute(() -> acceptInTransaction(command, fingerprint));
    }

    /** Exposed for deterministic application tests and future mapper contract tests. */
    public String fingerprint(AcceptPaymentRequestCommand command) {
        Objects.requireNonNull(command, "command");
        validateEnvelope(command);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            append(output, command.eventType());
            append(output, Integer.toString(command.eventVersion()));
            append(output, command.producer());
            append(output, command.aggregateType());
            append(output, command.aggregateId().toString());
            append(output, Long.toString(command.aggregateVersion()));
            append(output, command.orderId().toString());
            append(output, command.userId().toString());
            append(output, command.amount().setScale(4, RoundingMode.UNNECESSARY).toPlainString());
            append(output, command.currency().toUpperCase(Locale.ROOT));
            append(output, Long.toString(command.paymentDeadline().toEpochMilli()));
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("cannot encode canonical PaymentRequested fingerprint", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        } catch (ArithmeticException exception) {
            throw new InvalidPaymentRequestException("amount must be exactly representable at scale 4");
        }
    }

    private AcceptPaymentRequestResult acceptInTransaction(AcceptPaymentRequestCommand command,
            String fingerprint) {
        inbox.lockOrder(command.orderId());
        Instant now = clock.now();

        var byEvent = inbox.findByEventId(command.eventId());
        if (byEvent.isPresent()) {
            return existingEventResult(byEvent.get(), command, fingerprint, now);
        }

        var byOrder = inbox.findByOrderId(command.orderId());
        if (byOrder.isPresent()) {
            return existingOrderResult(byOrder.get(), command, fingerprint, now);
        }

        UUID paymentId = identities.newId();
        Payment payment = Payment.create(paymentId, command.orderId(), command.userId(), command.amount(),
                command.currency(), command.paymentDeadline(), now);
        inbox.receive(command.eventId(), command.eventType(), command.eventVersion(), command.orderId(),
                fingerprint, now);

        if (!now.isBefore(command.paymentDeadline())) {
            payment.expire(now);
            paymentWriter.save(payment);
            UUID outboxEventId = deadlineFailureOutbox(payment, command, now);
            inbox.markProcessed(command.eventId(), payment.id(), now);
            return AcceptPaymentRequestResult.expired(payment.id(), outboxEventId, fingerprint);
        }

        paymentWriter.save(payment);
        inbox.markProcessed(command.eventId(), payment.id(), now);
        return AcceptPaymentRequestResult.accepted(payment.id(), fingerprint, payment.status());
    }

    private AcceptPaymentRequestResult existingEventResult(PaymentCommandInboxPort.Receipt receipt,
            AcceptPaymentRequestCommand command, String fingerprint, Instant now) {
        UUID paymentId = requirePaymentId(receipt);
        if (receipt.payloadFingerprint().equals(fingerprint)) {
            return AcceptPaymentRequestResult.eventReplayed(paymentId, fingerprint, paymentStatus(paymentId));
        }
        inbox.markConflicted(receipt.eventId(), now);
        return conflict(paymentId, receipt.payloadFingerprint(), fingerprint,
                "event identity was reused with contradictory business content");
    }

    private AcceptPaymentRequestResult existingOrderResult(PaymentCommandInboxPort.Receipt receipt,
            AcceptPaymentRequestCommand command, String fingerprint, Instant now) {
        UUID paymentId = requirePaymentId(receipt);
        if (receipt.payloadFingerprint().equals(fingerprint)) {
            return AcceptPaymentRequestResult.businessReplayed(paymentId, fingerprint, paymentStatus(paymentId));
        }
        inbox.markConflicted(receipt.eventId(), now);
        return conflict(paymentId, receipt.payloadFingerprint(), fingerprint,
                "Order identity was reused with contradictory business content");
    }

    private AcceptPaymentRequestResult conflict(UUID paymentId, String establishedFingerprint,
            String incomingFingerprint, String reason) {
        PaymentStatus status = paymentStatus(paymentId);
        return new AcceptPaymentRequestResult(AcceptPaymentRequestResult.Outcome.CONFLICT, paymentId, null,
                incomingFingerprint, status,
                new ConflictDetails(paymentId, establishedFingerprint, incomingFingerprint, reason));
    }

    private PaymentStatus paymentStatus(UUID paymentId) {
        return payments.findById(paymentId).map(Payment::status).orElse(PaymentStatus.PENDING);
    }

    private UUID deadlineFailureOutbox(Payment payment, AcceptPaymentRequestCommand command, Instant now) {
        UUID eventId = UUID.nameUUIDFromBytes(("payment-deadline-failure:" + payment.id())
                .getBytes(StandardCharsets.UTF_8));
        String payload = deadlineFailurePayload(eventId, payment, command, now);
        outbox.save(new SavePaymentOutboxPort.OutboxRecord(eventId, payment.id(), payment.aggregateVersion(),
                PAYMENT_FAILURE_EVENT_TYPE, 1, paymentEventTopic, payment.orderId(), payload,
                command.traceparent(), command.tracestate(), "PENDING", 0, now, null, null, null, now));
        return eventId;
    }

    private String deadlineFailurePayload(UUID eventId, Payment payment,
            AcceptPaymentRequestCommand command, Instant failedAt) {
        return "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"PaymentFailed\","
                + "\"eventVersion\":1,"
                + "\"producer\":\"payment-service\","
                + "\"aggregateType\":\"PAYMENT\","
                + "\"aggregateId\":\"" + payment.id() + "\","
                + "\"aggregateVersion\":" + payment.aggregateVersion() + ","
                + "\"correlationId\":\"" + command.correlationId() + "\","
                + "\"causationId\":\"" + command.eventId() + "\","
                + "\"occurredAt\":\"" + failedAt + "\","
                + "\"data\":{"
                + "\"paymentId\":\"" + payment.id() + "\","
                + "\"orderId\":\"" + payment.orderId() + "\","
                + "\"amount\":" + payment.amount().toPlainString() + ","
                + "\"currency\":\"" + payment.currency() + "\","
                + "\"failedAt\":\"" + failedAt + "\","
                + "\"reason\":\"PAYMENT_DEADLINE_EXPIRED\","
                + "\"provider\":\"STRIPE\","
                + "\"providerSessionId\":null"
                + "}}";
    }

    private void validateEnvelope(AcceptPaymentRequestCommand command) {
        if (!EVENT_TYPE.equals(command.eventType()) || command.eventVersion() != EVENT_VERSION) {
            throw new InvalidPaymentRequestException("unsupported PaymentRequested event version");
        }
        if (!PRODUCER.equals(command.producer()) || !AGGREGATE_TYPE.equals(command.aggregateType())) {
            throw new InvalidPaymentRequestException("PaymentRequested producer or aggregate type is invalid");
        }
        if (!command.aggregateId().equals(command.orderId())) {
            throw new InvalidPaymentRequestException("aggregateId must equal orderId");
        }
        if (command.aggregateVersion() <= 0) {
            throw new InvalidPaymentRequestException("aggregateVersion must be positive");
        }
        BigDecimal amount = command.amount();
        if (amount.signum() <= 0 || amount.precision() > 19 || amount.scale() > 4) {
            throw new InvalidPaymentRequestException("amount must be positive with precision <= 19 and scale <= 4");
        }
        if (!command.currency().matches("[A-Z]{3}")) {
            throw new InvalidPaymentRequestException("currency must be three uppercase ASCII letters");
        }
    }

    private UUID requirePaymentId(PaymentCommandInboxPort.Receipt receipt) {
        if (receipt.paymentId() == null) {
            throw new IllegalStateException("processed Payment command has no Payment identity");
        }
        return receipt.paymentId();
    }

    private void append(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
