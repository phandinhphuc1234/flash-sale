package com.philia.flashsale.cart.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.cart.adapter.out.persistence.jpa.entity.CartJpaEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data boundary for owner-scoped Cart rows and PostgreSQL owner upsert. */
public interface CartJpaRepository extends JpaRepository<CartJpaEntity, UUID> {
    Optional<CartJpaEntity> findByOwnerId(UUID ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO carts (id, owner_id, created_at, updated_at)
            VALUES (:id, :ownerId, :createdAt, :updatedAt)
            ON CONFLICT (owner_id) DO UPDATE SET updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId,
            @Param("createdAt") Instant createdAt, @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE carts SET updated_at = :updatedAt WHERE owner_id = :ownerId", nativeQuery = true)
    int touchOwner(@Param("ownerId") UUID ownerId, @Param("updatedAt") Instant updatedAt);
}
