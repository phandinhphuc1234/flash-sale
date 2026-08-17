package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentAttemptJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.mapper.PaymentPersistenceMapper;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttempt;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter translating the Payment aggregate without leaking entities inward. */
@Component
@ConditionalOnBean(PaymentJpaRepository.class)
public class PaymentPersistenceAdapter implements LoadPaymentPort, SavePaymentPort {

    private final PaymentJpaRepository repository;
    private final PaymentPersistenceMapper mapper;

    public PaymentPersistenceAdapter(PaymentJpaRepository repository, PaymentPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(UUID paymentId) {
        return repository.findById(paymentId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Payment> findLockedById(UUID paymentId) {
        return repository.findLockedById(paymentId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = repository.findById(payment.id())
                .orElseGet(() -> mapper.toEntity(payment));
        entity.updateFrom(payment);
        synchronizeAttempts(entity, payment);
        return mapper.toDomain(repository.save(entity));
    }

    private void synchronizeAttempts(PaymentJpaEntity entity, Payment payment) {
        var existing = new HashMap<UUID, PaymentAttemptJpaEntity>();
        entity.getAttempts().forEach(attempt -> existing.put(attempt.getId(), attempt));
        for (PaymentAttempt attempt : payment.attempts()) {
            PaymentAttemptJpaEntity current = existing.remove(attempt.id());
            if (current == null) {
                entity.getAttempts().add(PaymentAttemptJpaEntity.from(attempt, entity));
            } else {
                current.updateFrom(attempt);
            }
        }
        entity.getAttempts().removeIf(attempt -> existing.containsKey(attempt.getId()));
    }
}
