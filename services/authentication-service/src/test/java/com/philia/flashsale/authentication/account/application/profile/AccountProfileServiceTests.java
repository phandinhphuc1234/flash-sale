package com.philia.flashsale.authentication.account.application.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;
import org.junit.jupiter.api.Test;

class AccountProfileServiceTests {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void prefersUsernameForLoginAndDisplayName() {
        Account account = Account.register("user@example.com", "Phuc", "encoded", NOW);
        UUID id = account.id();
        LoadAccountProfileService service = new LoadAccountProfileService(value -> Optional.of(account));

        AccountProfile profile = service.load(id);

        assertEquals(id, profile.id());
        assertEquals("Phuc", profile.login());
        assertEquals("Phuc", profile.username());
        assertEquals("Phuc", profile.displayName());
        assertEquals("user@example.com", profile.email());
        assertEquals("ACTIVE", profile.status());
        assertEquals(java.util.List.of("ROLE_USER"), profile.authorities());
    }

    @Test
    void fallsBackToEmailWhenUsernameIsAbsent() {
        Account account = Account.register("user@example.com", null, "encoded", NOW);
        LoadAccountProfileService service = new LoadAccountProfileService(value -> Optional.of(account));

        AccountProfile profile = service.load(account.id());

        assertEquals("user@example.com", profile.login());
        assertNull(profile.username());
        assertEquals("user@example.com", profile.displayName());
    }

    @Test
    void doesNotInventAProfileForAnUnknownAccount() {
        LoadAccountProfileService service = new LoadAccountProfileService(value -> Optional.empty());

        AccountFailure failure = assertThrows(AccountFailure.class, () -> service.load(UUID.randomUUID()));

        assertEquals("AUTH_ACCOUNT_NOT_FOUND", failure.code());
    }
}
