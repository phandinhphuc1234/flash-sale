package com.philia.flashsale.authentication.account.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Authentication account aggregate; it owns identity and account eligibility only. */
public final class Account {

    private final UUID id;
    private final String email;
    private final String emailNormalized;
    private final String username;
    private final String usernameNormalized;
    private final String passwordHash;
    private final AccountRole role;
    private AccountStatus status;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Account(UUID id, String email, String emailNormalized, String username, String usernameNormalized,
                    String passwordHash, AccountRole role, AccountStatus status, Instant lockedUntil,
                    Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.email = requireText(email, "email");
        this.emailNormalized = requireText(emailNormalized, "emailNormalized");
        this.username = username;
        this.usernameNormalized = usernameNormalized;
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role);
        this.status = Objects.requireNonNull(status);
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Account register(String email, String username, String encodedPassword, Instant now) {
        String normalizedEmail = normalizeRequired(email, "email");
        String normalizedUsername = normalizeOptional(username);
        return new Account(UUID.randomUUID(), email.trim(), normalizedEmail, trimToNull(username), normalizedUsername,
                encodedPassword, AccountRole.ROLE_USER, AccountStatus.ACTIVE, null, null, now, now);
    }

    public static Account restore(UUID id, String email, String emailNormalized, String username,
                                  String usernameNormalized, String passwordHash, AccountRole role,
                                  AccountStatus status, Instant lockedUntil, Instant lastLoginAt,
                                  Instant createdAt, Instant updatedAt) {
        return new Account(id, email, emailNormalized, username, usernameNormalized, passwordHash, role, status,
                lockedUntil, lastLoginAt, createdAt, updatedAt);
    }

    public boolean canAuthenticate(Instant now) {
        return status == AccountStatus.ACTIVE && (lockedUntil == null || lockedUntil.isBefore(now));
    }

    public void recordLogin(Instant now) {
        if (!canAuthenticate(now)) {
            throw new AccountFailure("AUTH_INVALID_CREDENTIALS", "Account cannot authenticate");
        }
        lastLoginAt = now;
        updatedAt = now;
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

    public static String normalizeRequired(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new AccountFailure("AUTH_VALIDATION_FAILED", field + " must not be blank");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static String normalizeOptional(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
