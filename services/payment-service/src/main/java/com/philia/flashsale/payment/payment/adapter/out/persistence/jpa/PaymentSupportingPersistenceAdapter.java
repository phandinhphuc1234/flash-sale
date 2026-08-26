package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentClientIdempotencyJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentProviderEventReceiptJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentAttemptJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentClientIdempotencyJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentProviderEventReceiptJpaRepository;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClientIdempotencyPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentProviderReceiptPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter for durable client idempotency and provider webhook receipt identities. */
@Component
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public class PaymentSupportingPersistenceAdapter implements PaymentClientIdempotencyPort,
        PaymentProviderReceiptPort {

    private final PaymentClientIdempotencyJpaRepository idempotencyRepository;
    private final PaymentProviderEventReceiptJpaRepository receiptRepository;
    private final PaymentJpaRepository paymentRepository;
    private final PaymentAttemptJpaRepository attemptRepository;

    public PaymentSupportingPersistenceAdapter(PaymentClientIdempotencyJpaRepository idempotencyRepository,
            PaymentProviderEventReceiptJpaRepository receiptRepository,
            PaymentJpaRepository paymentRepository,
            PaymentAttemptJpaRepository attemptRepository) {
        this.idempotencyRepository = idempotencyRepository;
        this.receiptRepository = receiptRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
    }

    @Override
    @Transactional
    public Optional<PaymentClientIdempotencyPort.Record> findLocked(String operation, String keyDigest) {
        return idempotencyRepository.findLockedByOperationAndKeyDigest(operation, keyDigest)
                .map(this::toIdempotencyRecord);
    }

    @Override
    @Transactional
    public Optional<PaymentClientIdempotencyPort.Record> findLockedById(UUID id) {
        return idempotencyRepository.findLockedById(id).map(this::toIdempotencyRecord);
    }

    @Override
    @Transactional
    public PaymentClientIdempotencyPort.Record create(UUID id, String operation, String keyDigest,
            UUID userId, UUID paymentId, String requestFingerprint, Instant createdAt) {
        var payment = paymentRepository.getReferenceById(paymentId);
        return toIdempotencyRecord(idempotencyRepository.save(PaymentClientIdempotencyJpaEntity.create(id, operation,
                keyDigest, userId, payment, requestFingerprint, createdAt)));
    }

    @Override
    @Transactional
    public PaymentClientIdempotencyPort.Record attachAttempt(UUID id, UUID attemptId,
            String outcomeStatus, Instant updatedAt) {
        // The caller locks the idempotency row first when this is the second
        // checkout transaction. Reusing the managed entity avoids issuing a
        // second SELECT ... FOR UPDATE (and preserves the idempotency ->
        // payment lock order used by CheckoutPersistenceService).
        return idempotencyRepository.findById(id).map(entity -> {
            entity.attachAttempt(attemptRepository.getReferenceById(attemptId), outcomeStatus, updatedAt);
            return toIdempotencyRecord(entity);
        }).orElseThrow(() -> new IllegalArgumentException("unknown payment idempotency record"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentProviderReceiptPort.Receipt> findByProviderEventId(String providerEventId) {
        return receiptRepository.findByProviderEventId(providerEventId).map(this::providerReceipt);
    }

    @Override
    @Transactional
    public PaymentProviderReceiptPort.Receipt receive(PaymentProviderReceiptPort.Receipt receipt) {
        var entity = PaymentProviderEventReceiptJpaEntity.received(receipt.id(), receipt.providerEventId(),
                receipt.providerEventType(), receipt.providerApiVersion(), receipt.liveMode(),
                receipt.providerObjectId(), receipt.orderId(), receipt.providerCreatedAt(),
                receipt.verifiedAt(), receipt.processingStatus());
        if (receipt.paymentId() != null) {
            paymentRepository.findById(receipt.paymentId()).ifPresent(entity::attachPayment);
        }
        if (receipt.attemptId() != null) {
            attemptRepository.findById(receipt.attemptId()).ifPresent(entity::attachAttempt);
        }
        receiptRepository.insertIfAbsent(entity.getId(), entity.getProviderEventId(),
                entity.getProviderEventType(), entity.getProviderApiVersion(), entity.isLiveMode(),
                entity.getProviderObjectId(), entity.getPayment() == null ? null : entity.getPayment().getId(),
                entity.getAttempt() == null ? null : entity.getAttempt().getId(), entity.getOrderId(),
                entity.getProviderCreatedAt(), entity.getVerifiedAt(), entity.getProcessingStatus());
        return receiptRepository.findByProviderEventId(receipt.providerEventId())
                .map(this::providerReceipt)
                .orElseThrow(() -> new IllegalStateException("provider receipt insert was not observable"));
    }

    @Override
    @Transactional
    public List<PaymentProviderReceiptPort.Receipt> claimReceiptBatch(Instant now, int batchSize,
            String leaseOwner, Instant leaseUntil) {
        List<PaymentProviderReceiptPort.Receipt> claimed = new ArrayList<>();
        for (var receiptEntity : receiptRepository.claimCandidates(now, batchSize)) {
            receiptEntity.claim(leaseOwner, leaseUntil);
            claimed.add(providerReceipt(receiptRepository.save(receiptEntity)));
        }
        return claimed;
    }

    @Override
    @Transactional
    public void markProcessed(UUID receiptId, Instant processedAt) {
        receiptRepository.findById(receiptId).ifPresent(row -> {
            row.markProcessed(processedAt);
            receiptRepository.save(row);
        });
    }

    @Override
    @Transactional
    public void markIgnored(UUID receiptId, Instant processedAt) {
        receiptRepository.findById(receiptId).ifPresent(row -> {
            row.markIgnored(processedAt);
            receiptRepository.save(row);
        });
    }

    @Override
    @Transactional
    public void reschedule(UUID receiptId, String errorCode, Instant nextAttemptAt) {
        receiptRepository.findById(receiptId).ifPresent(row -> {
            row.reschedule(errorCode, nextAttemptAt);
            receiptRepository.save(row);
        });
    }

    @Override
    @Transactional
    public void markReceiptManualReview(UUID receiptId, String errorCode, Instant observedAt) {
        receiptRepository.findById(receiptId).ifPresent(row -> {
            row.manualReview(errorCode, observedAt);
            receiptRepository.save(row);
        });
    }


    private PaymentClientIdempotencyPort.Record toIdempotencyRecord(PaymentClientIdempotencyJpaEntity entity) {
        return new PaymentClientIdempotencyPort.Record(entity.getId(), entity.getOperation(), entity.getKeyDigest(),
                entity.getUserId(), entity.getPayment().getId(), entity.getRequestFingerprint(),
                entity.getAttempt() == null ? null : entity.getAttempt().getId(), entity.getOutcomeStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private PaymentProviderReceiptPort.Receipt providerReceipt(PaymentProviderEventReceiptJpaEntity entity) {
        return new PaymentProviderReceiptPort.Receipt(entity.getId(), entity.getProviderEventId(),
                entity.getProviderEventType(), entity.isLiveMode(), entity.getProviderApiVersion(),
                entity.getProviderObjectId(),
                entity.getPayment() == null ? null : entity.getPayment().getId(),
                entity.getAttempt() == null ? null : entity.getAttempt().getId(), entity.getOrderId(),
                entity.getProviderCreatedAt(), entity.getVerifiedAt(), entity.getProcessingStatus(),
                entity.getLastErrorCode(), entity.getAttemptCount(), entity.getNextAttemptAt());
    }

}
