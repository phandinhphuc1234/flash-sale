package com.philia.flashsale.cart.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA representation of the Cart aggregate shell; Product fields never belong here. */
@Entity
@Table(name = "carts")
public class CartJpaEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, unique = true)
    private UUID ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "version", nullable = false)
    private long version;

    protected CartJpaEntity() { }

    public CartJpaEntity(UUID id, UUID ownerId, Instant createdAt, Instant updatedAt) {
        this(id, ownerId, createdAt, updatedAt, 0);
    }

    public CartJpaEntity(UUID id, UUID ownerId, Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
