package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.ReservationCommandInboxJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationCommandInboxJpaRepository extends JpaRepository<ReservationCommandInboxJpaEntity, UUID> {
}
