package com.philia.flashsale.authentication.session.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Locked current-token lookup and refresh-chain persistence queries. */
public interface RefreshCredentialJpaRepository extends JpaRepository<RefreshCredentialJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshCredentialJpaEntity> findByTokenHash(String tokenHash);
    List<RefreshCredentialJpaEntity> findAllBySessionId(UUID sessionId);

    @Modifying
    @Query("delete from RefreshCredentialJpaEntity r where r.expiresAt < :cutoff "
            + "or (r.usedAt is not null and r.usedAt < :cutoff) "
            + "or (r.revokedAt is not null and r.revokedAt < :cutoff)")
    int deleteRetainedExpired(@Param("cutoff") Instant cutoff);
}
