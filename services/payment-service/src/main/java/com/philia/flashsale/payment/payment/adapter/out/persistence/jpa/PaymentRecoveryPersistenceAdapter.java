package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentRecoveryWorkJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentAttemptJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentRecoveryWorkJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.mapper.PaymentPersistenceMapper;
import com.philia.flashsale.payment.payment.application.port.out.LoadDuePaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentRecoveryWorkPort;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter for leased recovery work and deadline candidate projection. */
@Component
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public class PaymentRecoveryPersistenceAdapter implements PaymentRecoveryWorkPort, LoadDuePaymentPort {

    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "IN_PROGRESS");
    private static final List<PaymentStatus> DEADLINE_STATUSES =
            List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.UNKNOWN);

    private final PaymentRecoveryWorkJpaRepository recoveryRepository;
    private final PaymentJpaRepository paymentRepository;
    private final PaymentAttemptJpaRepository attemptRepository;
    private final PaymentPersistenceMapper mapper;

    public PaymentRecoveryPersistenceAdapter(PaymentRecoveryWorkJpaRepository recoveryRepository,
            PaymentJpaRepository paymentRepository, PaymentAttemptJpaRepository attemptRepository,
            PaymentPersistenceMapper mapper) {
        this.recoveryRepository = recoveryRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PaymentRecoveryWorkPort.Work schedule(PaymentRecoveryWorkPort.Work work) {
        List<PaymentRecoveryWorkJpaEntity> existing = work.attemptId() == null
                ? recoveryRepository.findByPayment_IdAndWorkTypeAndStatusIn(work.paymentId(), work.workType(), ACTIVE_STATUSES)
                : recoveryRepository.findByAttempt_IdAndWorkTypeAndStatusIn(work.attemptId(), work.workType(), ACTIVE_STATUSES);
        if (!existing.isEmpty()) {
            return toWork(existing.get(0));
        }
        var payment = paymentRepository.getReferenceById(work.paymentId());
        var attempt = work.attemptId() == null ? null : attemptRepository.getReferenceById(work.attemptId());
        var entity = PaymentRecoveryWorkJpaEntity.pending(work.id(), payment, attempt, work.workType(),
                work.providerIdempotencyKey(), work.safeReplayUntil(), work.createdAt());
        return toWork(recoveryRepository.save(entity));
    }

    @Override
    @Transactional
    public List<PaymentRecoveryWorkPort.Work> claimWorkBatch(Instant now, int batchSize,
            String leaseOwner, Instant leaseUntil) {
        return recoveryRepository.claimCandidates(now, batchSize).stream()
                .map(entity -> {
                    entity.claim(leaseOwner, leaseUntil, now);
                    return toWork(recoveryRepository.save(entity));
                })
                .toList();
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
    public void reschedule(UUID workId, String errorCode, Instant nextAttemptAt, Instant observedAt) {
        recoveryRepository.findById(workId).ifPresent(row -> {
            row.reschedule(safeError(errorCode), nextAttemptAt, observedAt);
            recoveryRepository.save(row);
        });
    }

    @Override
    @Transactional
    public void markManualReview(UUID workId, String errorCode, Instant observedAt) {
        recoveryRepository.findById(workId).ifPresent(row -> {
            row.manualReview(safeError(errorCode), observedAt);
            recoveryRepository.save(row);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findDeadlineCandidates(Instant now, int batchSize) {
        return paymentRepository.findByPaymentDeadlineLessThanEqualAndStatusInOrderByPaymentDeadlineAsc(
                        now, DEADLINE_STATUSES, PageRequest.of(0, Math.max(1, batchSize))).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private PaymentRecoveryWorkPort.Work toWork(PaymentRecoveryWorkJpaEntity entity) {
        return new PaymentRecoveryWorkPort.Work(entity.getId(), entity.getPayment().getId(),
                entity.getAttempt() == null ? null : entity.getAttempt().getId(), entity.getWorkType(),
                entity.getStatus(), entity.getProviderIdempotencyKey(), entity.getSafeReplayUntil(),
                entity.getAttemptCount(), entity.getNextAttemptAt(), entity.getLeaseOwner(), entity.getLeaseUntil(),
                entity.getLastErrorCode(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String safeError(String value) {
        if (value == null || value.isBlank()) {
            return "RECOVERY_ERROR";
        }
        return value.length() <= 64 ? value.replaceAll("[^A-Za-z0-9_.-]", "_")
                : value.substring(0, 64).replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
