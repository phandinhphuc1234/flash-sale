package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data boundary for the Payment aggregate identity and pessimistic claims. */
public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentJpaEntity p where p.id = :id")
    Optional<PaymentJpaEntity> findLockedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentJpaEntity p where p.orderId = :orderId")
    Optional<PaymentJpaEntity> findLockedByOrderId(@Param("orderId") UUID orderId);

    List<PaymentJpaEntity> findByPaymentDeadlineLessThanEqualAndStatusInOrderByPaymentDeadlineAsc(
            java.time.Instant deadline, List<PaymentStatus> statuses,
            org.springframework.data.domain.Pageable pageable);
}
