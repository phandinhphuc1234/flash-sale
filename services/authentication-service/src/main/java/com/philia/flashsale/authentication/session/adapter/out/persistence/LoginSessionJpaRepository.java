package com.philia.flashsale.authentication.session.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data queries for subject revocation and retained inactive sessions. */
public interface LoginSessionJpaRepository extends JpaRepository<LoginSessionJpaEntity, UUID> {
    List<LoginSessionJpaEntity> findAllByUserIdAndStatus(UUID userId, com.philia.flashsale.authentication.session.domain.LoginSessionStatus status);

    @Modifying
    @Query("update LoginSessionJpaEntity s set s.status = com.philia.flashsale.authentication.session.domain.LoginSessionStatus.REVOKED, s.revokedAt = :now, s.revokeReason = :reason where s.userId = :userId and s.status = com.philia.flashsale.authentication.session.domain.LoginSessionStatus.ACTIVE")
    int revokeAllByUserId(UUID userId, java.time.Instant now, String reason);

    @Modifying
    @Query("delete from LoginSessionJpaEntity s where s.status <> com.philia.flashsale.authentication.session.domain.LoginSessionStatus.ACTIVE "
            + "and (s.expiresAt < :now or (s.revokedAt is not null and s.revokedAt < :cutoff))")
    int deleteRetainedInactive(@Param("now") java.time.Instant now, @Param("cutoff") java.time.Instant cutoff);
}
