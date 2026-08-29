package com.philia.flashsale.cart.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.cart.adapter.out.persistence.jpa.entity.CartItemJpaEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data boundary for composite Cart item identity and atomic quantity replacement. */
public interface CartItemJpaRepository extends JpaRepository<CartItemJpaEntity, CartItemJpaEntity.CartItemId> {
    Optional<CartItemJpaEntity> findByIdCartIdAndIdVariantId(UUID cartId, UUID variantId);

    @Query("select item from CartItemJpaEntity item where item.id.cartId = :cartId "
            + "order by item.updatedAt desc, item.id.variantId asc")
    List<CartItemJpaEntity> findByCartIdOrderByUpdatedAtDescVariantIdAsc(@Param("cartId") UUID cartId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO cart_items (cart_id, variant_id, quantity, created_at, updated_at)
            VALUES (:cartId, :variantId, :quantity, :createdAt, :updatedAt)
            ON CONFLICT (cart_id, variant_id)
            DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsert(@Param("cartId") UUID cartId, @Param("variantId") UUID variantId,
            @Param("quantity") int quantity, @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItemJpaEntity item where item.id.cartId = :cartId and item.id.variantId = :variantId")
    int deleteByCartIdAndVariantId(@Param("cartId") UUID cartId, @Param("variantId") UUID variantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItemJpaEntity item where item.id.cartId = :cartId")
    int deleteByCartId(@Param("cartId") UUID cartId);
}
