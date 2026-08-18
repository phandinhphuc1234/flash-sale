package com.philia.flashsale.payment.payment.application.service;

import com.philia.flashsale.payment.payment.application.exception.PaymentCheckoutException;
import com.philia.flashsale.payment.payment.application.exception.PaymentCheckoutNotFoundException;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutCommand;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderCheckoutState;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClientIdempotencyPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentRecoveryWorkPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttempt;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttemptStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Owns the two short database transactions surrounding a remote provider call. */
public final class CheckoutPersistenceService {
    public static final String OPERATION = "CREATE_OR_RESUME_CHECKOUT";
    public static final String WORK_TYPE = "CREATE_SESSION";

    private final LoadPaymentPort payments;
    private final SavePaymentPort paymentWriter;
    private final PaymentClientIdempotencyPort clientIdempotency;
    private final PaymentRecoveryWorkPort recovery;
    private final PaymentClockPort clock;
    private final PaymentIdentityPort identities;
    private final PaymentTransactionPort transactions;
    private final Duration safeReplayWindow;
    private final String successUrl;
    private final String cancelUrl;

    public CheckoutPersistenceService(LoadPaymentPort payments, SavePaymentPort paymentWriter,
            PaymentClientIdempotencyPort clientIdempotency, PaymentRecoveryWorkPort recovery,
            PaymentClockPort clock, PaymentIdentityPort identities, PaymentTransactionPort transactions) {
        this(payments, paymentWriter, clientIdempotency, recovery, clock, identities, transactions,
                Duration.ofHours(23), "http://localhost:3000/payments/success",
                "http://localhost:3000/payments/cancel");
    }

    public CheckoutPersistenceService(LoadPaymentPort payments, SavePaymentPort paymentWriter,
            PaymentClientIdempotencyPort clientIdempotency, PaymentRecoveryWorkPort recovery,
            PaymentClockPort clock, PaymentIdentityPort identities, PaymentTransactionPort transactions,
            Duration safeReplayWindow) {
        this(payments, paymentWriter, clientIdempotency, recovery, clock, identities, transactions,
                safeReplayWindow, "http://localhost:3000/payments/success",
                "http://localhost:3000/payments/cancel");
    }

    public CheckoutPersistenceService(LoadPaymentPort payments, SavePaymentPort paymentWriter,
            PaymentClientIdempotencyPort clientIdempotency, PaymentRecoveryWorkPort recovery,
            PaymentClockPort clock, PaymentIdentityPort identities, PaymentTransactionPort transactions,
            Duration safeReplayWindow, String successUrl, String cancelUrl) {
        this.payments = Objects.requireNonNull(payments, "payments");
        this.paymentWriter = Objects.requireNonNull(paymentWriter, "paymentWriter");
        this.clientIdempotency = Objects.requireNonNull(clientIdempotency, "clientIdempotency");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.safeReplayWindow = Objects.requireNonNull(safeReplayWindow, "safeReplayWindow");
        this.successUrl = Objects.requireNonNull(successUrl, "successUrl");
        this.cancelUrl = Objects.requireNonNull(cancelUrl, "cancelUrl");
    }

    public Allocation allocate(StartCheckoutCommand command) {
        String keyDigest = sha256(command.idempotencyKey());
        String requestFingerprint = requestFingerprint(command);
        return transactions.execute(() -> allocateInTransaction(command, keyDigest, requestFingerprint));
    }

    public StartCheckoutSnapshot converge(Allocation allocation, HostedCheckoutResult result) {
        return transactions.execute(() -> convergeInTransaction(allocation, result));
    }

    private Allocation allocateInTransaction(StartCheckoutCommand command, String keyDigest,
            String requestFingerprint) {
        Instant now = clock.now();
        var existing = clientIdempotency.findLocked(OPERATION, keyDigest);
        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.userId().equals(command.userId()) || !record.paymentId().equals(command.paymentId())
                    || !record.requestFingerprint().equals(requestFingerprint)) {
                throw new PaymentCheckoutException(PaymentCheckoutException.Outcome.IDEMPOTENCY_CONFLICT);
            }
            Payment payment = payments.findLockedById(record.paymentId())
                    .orElseThrow(PaymentCheckoutNotFoundException::new);
            PaymentAttempt attempt = findAttempt(payment, record.attemptId());
            if (attempt == null) {
                return new Allocation(record.id(), payment.id(), null, null, null, null, false,
                        payment.status(), payment.paymentDeadline());
            }
            if (attempt.providerSessionId() != null) {
                return new Allocation(record.id(), payment.id(), attempt.id(), null,
                        attempt.providerIdempotencyKey(), null, false, payment.status(),
                        payment.paymentDeadline(), new HostedCheckoutRetrieveRequest(attempt.providerSessionId()));
            }
            return new Allocation(record.id(), payment.id(), attempt.id(), null,
                    attempt.providerIdempotencyKey(), createRequest(payment, attempt), false,
                    payment.status(), payment.paymentDeadline(), null);
        }

        Payment payment = payments.findLockedById(command.paymentId())
                .orElseThrow(PaymentCheckoutNotFoundException::new);
        if (!payment.userId().equals(command.userId())) {
            throw new PaymentCheckoutNotFoundException();
        }
        UUID attemptId = identities.newId();
        UUID providerWorkId = identities.newId();
        UUID idempotencyId = identities.newId();
        String providerKey = "payment-checkout-" + attemptId;
        PaymentAttempt attempt;
        try {
            attempt = payment.allocateAttempt(attemptId, providerKey, now,
                    now.plus(safeReplayWindow));
        } catch (RuntimeException exception) {
            throw classifyDomainAllocation(exception);
        }
        attempt.markSubmitted(now);
        paymentWriter.save(payment);
        clientIdempotency.create(idempotencyId, OPERATION, keyDigest, command.userId(), payment.id(),
                requestFingerprint, now);
        clientIdempotency.attachAttempt(idempotencyId, attempt.id(), "ACCEPTED", now);
        recovery.schedule(new PaymentRecoveryWorkPort.Work(providerWorkId, payment.id(), attempt.id(), WORK_TYPE,
                "PENDING", providerKey, now.plus(safeReplayWindow), 0, now, null, null, null, now, now));
        return new Allocation(idempotencyId, payment.id(), attempt.id(), providerWorkId, providerKey,
                createRequest(payment, attempt), true, payment.status(), payment.paymentDeadline(), null);
    }

    private StartCheckoutSnapshot convergeInTransaction(Allocation allocation, HostedCheckoutResult result) {
        Instant now = clock.now();
        Payment payment = payments.findLockedById(allocation.paymentId())
                .orElseThrow(PaymentCheckoutNotFoundException::new);
        PaymentAttempt attempt = findAttempt(payment, allocation.attemptId());
        if (attempt == null) {
            return new StartCheckoutSnapshot(StartCheckoutResultKind.RECOVERING, payment.status(), null,
                    payment.paymentDeadline());
        }
        String url = null;
        StartCheckoutResultKind kind = allocation.newAllocation()
                ? StartCheckoutResultKind.CREATED : StartCheckoutResultKind.REPLAYED;
        switch (result.state()) {
            case OPEN -> {
                if (attempt.status() == PaymentAttemptStatus.CREATING
                        || attempt.status() == PaymentAttemptStatus.UNKNOWN) {
                    payment.markAttemptOpen(attempt.id(), result.providerSessionId(), result.providerExpiresAt(), now);
                    paymentWriter.save(payment);
                }
                url = result.checkoutUrl();
            }
            case FAILED, EXPIRED -> {
                if (attempt.status().unresolved()) {
                    payment.markProviderTerminalFailure(attempt.id(), now);
                    paymentWriter.save(payment);
                }
            }
            case UNKNOWN, PROCESSING, PAID -> {
                if (result.state() == ProviderCheckoutState.UNKNOWN && attempt.status().unresolved()) {
                    payment.markAttemptUnknown(attempt.id(), "PROVIDER_UNAVAILABLE", now);
                    paymentWriter.save(payment);
                }
            }
        }
        if (allocation.idempotencyId() != null && allocation.attemptId() != null) {
            String outcome = url == null ? "RECOVERING" : "AVAILABLE";
            clientIdempotency.attachAttempt(allocation.idempotencyId(), allocation.attemptId(), outcome, now);
        }
        if (allocation.recoveryWorkId() != null && url != null) {
            recovery.complete(allocation.recoveryWorkId(), now);
        }
        if (url == null || result.state() == ProviderCheckoutState.UNKNOWN) {
            kind = StartCheckoutResultKind.RECOVERING;
        }
        return new StartCheckoutSnapshot(kind, payment.status(), url, payment.paymentDeadline());
    }

    private HostedCheckoutCreateRequest createRequest(Payment payment, PaymentAttempt attempt) {
        return new HostedCheckoutCreateRequest(payment.id().toString(), payment.orderId().toString(),
                attempt.id().toString(), payment.amount(), payment.currency(), attempt.providerIdempotencyKey(),
                payment.paymentDeadline(), successUrl, cancelUrl, Map.of("orderId", payment.orderId().toString(),
                        "paymentId", payment.id().toString(), "attemptId", attempt.id().toString()));
    }

    private PaymentAttempt findAttempt(Payment payment, UUID attemptId) {
        return attemptId == null ? payment.activeAttempt()
                : payment.attempts().stream().filter(item -> item.id().equals(attemptId)).findFirst().orElse(null);
    }

    private PaymentCheckoutException classifyDomainAllocation(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        if (message.contains("deadline")) {
            return new PaymentCheckoutException(PaymentCheckoutException.Outcome.DEADLINE_PASSED);
        }
        if (message.contains("limit")) {
            return new PaymentCheckoutException(PaymentCheckoutException.Outcome.ATTEMPT_LIMIT_REACHED);
        }
        if (message.contains("unresolved")) {
            return new PaymentCheckoutException(PaymentCheckoutException.Outcome.CHECKOUT_IN_PROGRESS);
        }
        return new PaymentCheckoutException(PaymentCheckoutException.Outcome.NOT_PAYABLE);
    }

    public String requestFingerprint(StartCheckoutCommand command) {
        return sha256("POST|/api/v1/payments/" + command.paymentId()
                + "/checkout-sessions|" + command.userId());
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    public enum StartCheckoutResultKind { CREATED, REPLAYED, RECOVERING }

    public record Allocation(UUID idempotencyId, UUID paymentId, UUID attemptId, UUID recoveryWorkId,
            String providerIdempotencyKey, HostedCheckoutCreateRequest createRequest, boolean newAllocation,
            com.philia.flashsale.payment.payment.domain.model.PaymentStatus paymentStatus,
            Instant paymentDeadline, HostedCheckoutRetrieveRequest retrieveRequest) {
        public Allocation(UUID idempotencyId, UUID paymentId, UUID attemptId, UUID recoveryWorkId,
                String providerIdempotencyKey, HostedCheckoutCreateRequest createRequest, boolean newAllocation,
                com.philia.flashsale.payment.payment.domain.model.PaymentStatus paymentStatus,
                Instant paymentDeadline) {
            this(idempotencyId, paymentId, attemptId, recoveryWorkId, providerIdempotencyKey, createRequest,
                    newAllocation, paymentStatus, paymentDeadline, null);
        }
    }

    public record StartCheckoutSnapshot(StartCheckoutResultKind kind,
            com.philia.flashsale.payment.payment.domain.model.PaymentStatus paymentStatus,
            String checkoutUrl, Instant paymentDeadline) {
    }
}
