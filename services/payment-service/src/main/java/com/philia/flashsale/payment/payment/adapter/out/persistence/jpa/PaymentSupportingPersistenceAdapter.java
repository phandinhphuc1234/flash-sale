package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentClientIdempotencyJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentProviderEventReceiptJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentRecoveryWorkJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentAttemptJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentClientIdempotencyJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentProviderEventReceiptJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentRecoveryWorkJpaRepository;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClientIdempotencyPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentProviderReceiptPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentRecoveryWorkPort;
import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter for durable identities and worker queues other than the aggregate itself. */
@Component
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public class PaymentSupportingPersistenceAdapter implements PaymentClientIdempotencyPort,
        PaymentProviderReceiptPort, PaymentRecoveryWorkPort {

    private final PaymentClientIdempotencyJpaRepository idempotencyRepository;
    private final PaymentProviderEventReceiptJpaRepository receiptRepository;
    private final PaymentRecoveryWorkJpaRepository recoveryRepository;
    private final PaymentJpaRepository paymentRepository;
    private final PaymentAttemptJpaRepository attemptRepository;

    public PaymentSupportingPersistenceAdapter(PaymentClientIdempotencyJpaRepository idempotencyRepository,
            PaymentProviderEventReceiptJpaRepository receiptRepository,
            PaymentRecoveryWorkJpaRepository recoveryRepository, PaymentJpaRepository paymentRepository,
            PaymentAttemptJpaRepository attemptRepository) {
        this.idempotencyRepository = idempotencyRepository;
        this.receiptRepository = receiptRepository;
        this.recoveryRepository = recoveryRepository;
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
        return idempotencyRepository.findById(id).map(entity -> {
            entity.attachAttempt(attemptRepository.getReferenceById(attemptId), outcomeStatus, updatedAt);
            return toIdempotencyRecord(idempotencyRepository.save(entity));
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
                receipt.providerEventType(), receipt.liveMode(), receipt.providerCreatedAt(), receipt.verifiedAt());
        return providerReceipt(receiptRepository.save(entity));
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
    public PaymentRecoveryWorkPort.Work schedule(PaymentRecoveryWorkPort.Work work) {
        var payment = paymentRepository.getReferenceById(work.paymentId());
        var attempt = work.attemptId() == null ? null : attemptRepository.getReferenceById(work.attemptId());
        var entity = PaymentRecoveryWorkJpaEntity.pending(work.id(), payment, attempt, work.workType(),
                work.providerIdempotencyKey(), work.safeReplayUntil(), work.createdAt());
        return recoveryWork(recoveryRepository.save(entity));
    }

    @Override
    @Transactional
    public List<PaymentRecoveryWorkPort.Work> claimWorkBatch(Instant now, int batchSize, String leaseOwner,
            Instant leaseUntil) {
        List<PaymentRecoveryWorkPort.Work> claimed = new ArrayList<>();
        for (var workEntity : recoveryRepository.claimCandidates(now, batchSize)) {
            workEntity.claim(leaseOwner, leaseUntil, now);
            claimed.add(recoveryWork(recoveryRepository.save(workEntity)));
        }
        return claimed;
    }

    @Override
    @Transactional
    public void complete(UUID workId, Instant completedAt) {
        recoveryRepository.findById(workId).ifPresent(row -> {
            row.complete(completedAt);
            recoveryRepository.save(row);
        });
    }

    @Override
    @Transactional
    public void markManualReview(UUID workId, String errorCode, Instant observedAt) {
        recoveryRepository.findById(workId).ifPresent(row -> {
            row.manualReview(errorCode, observedAt);
            recoveryRepository.save(row);
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
                entity.getProviderEventType(), entity.isLiveMode(), entity.getProviderObjectId(),
                entity.getPayment() == null ? null : entity.getPayment().getId(),
                entity.getAttempt() == null ? null : entity.getAttempt().getId(), entity.getOrderId(),
                entity.getProviderCreatedAt(), entity.getVerifiedAt(), entity.getProcessingStatus(),
                entity.getLastErrorCode(), entity.getAttemptCount(), entity.getNextAttemptAt());
    }

    private PaymentRecoveryWorkPort.Work recoveryWork(PaymentRecoveryWorkJpaEntity entity) {
        return new PaymentRecoveryWorkPort.Work(entity.getId(), entity.getPayment().getId(),
                entity.getAttempt() == null ? null : entity.getAttempt().getId(), entity.getWorkType(),
                entity.getStatus(), entity.getProviderIdempotencyKey(), entity.getSafeReplayUntil(),
                entity.getAttemptCount(), entity.getNextAttemptAt(), entity.getLeaseOwner(), entity.getLeaseUntil(),
                entity.getLastErrorCode(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
