package com.philia.flashsale.authentication.account.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.authentication.account.domain.AccountRole;
import com.philia.flashsale.authentication.account.domain.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
/** Persistence-only mapping for the Authentication-owned users table. */
public class AccountJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 320)
    private String email;
    @Column(name = "email_normalized", nullable = false, unique = true, length = 320)
    private String emailNormalized;
    @Column(length = 100)
    private String username;
    @Column(name = "username_normalized", unique = true, length = 100)
    private String usernameNormalized;
    @Column(name = "password_hash", nullable = false, length = 512)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected AccountJpaEntity() { }

    public AccountJpaEntity(UUID id, String email, String emailNormalized, String username,
                            String usernameNormalized, String passwordHash, AccountRole role,
                            AccountStatus status, Instant lockedUntil, Instant lastLoginAt,
                            Instant createdAt, Instant updatedAt) {
        this.id = id; this.email = email; this.emailNormalized = emailNormalized; this.username = username;
        this.usernameNormalized = usernameNormalized; this.passwordHash = passwordHash; this.role = role;
        this.status = status; this.lockedUntil = lockedUntil; this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    public UUID id() { return id; }
    public String email() { return email; }
    public String emailNormalized() { return emailNormalized; }
    public String username() { return username; }
    public String usernameNormalized() { return usernameNormalized; }
    public String passwordHash() { return passwordHash; }
    public AccountRole role() { return role; }
    public AccountStatus status() { return status; }
    public Instant lockedUntil() { return lockedUntil; }
    public Instant lastLoginAt() { return lastLoginAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void updateLogin(Instant lastLoginAt, Instant updatedAt) { this.lastLoginAt = lastLoginAt; this.updatedAt = updatedAt; }
}
