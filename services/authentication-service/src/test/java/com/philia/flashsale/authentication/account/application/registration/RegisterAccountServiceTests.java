package com.philia.flashsale.authentication.account.application.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;

class RegisterAccountServiceTests {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void encodesPasswordAndForcesPublicRole() {
        AtomicReference<String> encodedInput = new AtomicReference<>();
        AtomicReference<Account> saved = new AtomicReference<>();
        RegisterAccountPort port = new RegisterAccountPort() {
            public Optional<Account> findByEmailNormalized(String value) { return Optional.empty(); }
            public Optional<Account> findByUsernameNormalized(String value) { return Optional.empty(); }
            public Account save(Account value) { saved.set(value); return value; }
        };
        EncodePasswordPort encoder = raw -> { encodedInput.set(raw); return "encoded"; };
        RegisterAccountService service = new RegisterAccountService(port, encoder,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RegisterAccountResult result = service.register(new RegisterAccountCommand(
                " User@example.com ", " Shopper ", "correct horse battery staple"));

        assertEquals("correct horse battery staple", encodedInput.get());
        assertEquals("User@example.com", result.email());
        assertEquals("ROLE_USER", result.role());
        assertEquals("ACTIVE", result.status());
        assertEquals("encoded", saved.get().passwordHash());
    }

    @Test
    void rejectsPasswordsOutsideApprovedCodePointRange() {
        RegisterAccountPort port = new RegisterAccountPort() {
            public Optional<Account> findByEmailNormalized(String value) { return Optional.empty(); }
            public Optional<Account> findByUsernameNormalized(String value) { return Optional.empty(); }
            public Account save(Account value) { return value; }
        };
        RegisterAccountService service = new RegisterAccountService(port, raw -> "hash",
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(AccountFailure.class, () -> service.register(new RegisterAccountCommand("a@b.test", "a", "short")));
    }
}
