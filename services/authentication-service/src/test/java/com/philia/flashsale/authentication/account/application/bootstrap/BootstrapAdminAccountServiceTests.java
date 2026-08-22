package com.philia.flashsale.authentication.account.application.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;
import com.philia.flashsale.authentication.account.domain.AccountRole;
import com.philia.flashsale.authentication.account.domain.AccountStatus;

class BootstrapAdminAccountServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

    @Test
    void createsActiveAdminWithEncodedPassword() {
        InMemoryPort port = new InMemoryPort();
        BootstrapAdminAccountService service = new BootstrapAdminAccountService(
                port, raw -> "argon2:" + raw, Clock.fixed(NOW, ZoneOffset.UTC));

        BootstrapAdminAccountResult result = service.bootstrap(new BootstrapAdminAccountCommand(
                " Admin@Example.test ", " ops-admin ", "correct horse battery"));

        assertTrue(result.created());
        Account saved = port.byEmail("admin@example.test");
        assertEquals(AccountRole.ROLE_ADMIN, saved.role());
        assertEquals(AccountStatus.ACTIVE, saved.status());
        assertEquals("argon2:correct horse battery", saved.passwordHash());
    }

    @Test
    void sameActiveAdminIsIdempotentAndDoesNotReencodePassword() {
        InMemoryPort port = new InMemoryPort();
        Account existing = Account.bootstrapAdmin("admin@example.test", "ops-admin", "original-hash", NOW);
        port.save(existing);
        AtomicBoolean encoded = new AtomicBoolean();
        BootstrapAdminAccountService service = new BootstrapAdminAccountService(
                port, raw -> { encoded.set(true); return "replacement"; }, Clock.fixed(NOW, ZoneOffset.UTC));

        BootstrapAdminAccountResult result = service.bootstrap(new BootstrapAdminAccountCommand(
                "admin@example.test", "ops-admin", "another valid password"));

        assertFalse(result.created());
        assertEquals(existing.id(), result.accountId());
        assertFalse(encoded.get());
        assertEquals("original-hash", port.byEmail("admin@example.test").passwordHash());
    }

    @Test
    void rejectsRoleUserCollisionWithoutMutation() {
        InMemoryPort port = new InMemoryPort();
        Account user = Account.register("admin@example.test", "ops-admin", "user-hash", NOW);
        port.save(user);
        BootstrapAdminAccountService service = new BootstrapAdminAccountService(
                port, raw -> "hash", Clock.fixed(NOW, ZoneOffset.UTC));

        AccountFailure failure = assertThrows(AccountFailure.class, () -> service.bootstrap(
                new BootstrapAdminAccountCommand("admin@example.test", "ops-admin", "valid password")));

        assertEquals("AUTH_ADMIN_BOOTSTRAP_CONFLICT", failure.code());
        assertEquals(AccountRole.ROLE_USER, port.byEmail("admin@example.test").role());
    }

    @Test
    void rejectsEmailAndUsernameOwnedByDifferentIdentities() {
        InMemoryPort port = new InMemoryPort();
        port.save(Account.bootstrapAdmin("admin@example.test", "first-admin", "hash-1", NOW));
        port.save(Account.bootstrapAdmin("other@example.test", "ops-admin", "hash-2", NOW));
        BootstrapAdminAccountService service = new BootstrapAdminAccountService(
                port, raw -> "hash", Clock.fixed(NOW, ZoneOffset.UTC));

        AccountFailure failure = assertThrows(AccountFailure.class, () -> service.bootstrap(
                new BootstrapAdminAccountCommand("admin@example.test", "ops-admin", "valid password")));

        assertEquals("AUTH_ADMIN_BOOTSTRAP_CONFLICT", failure.code());
    }

    @Test
    void rejectsPasswordOutsideExistingPolicy() {
        BootstrapAdminAccountService service = new BootstrapAdminAccountService(
                new InMemoryPort(), raw -> "hash", Clock.fixed(NOW, ZoneOffset.UTC));

        AccountFailure failure = assertThrows(AccountFailure.class, () -> service.bootstrap(
                new BootstrapAdminAccountCommand("admin@example.test", "ops-admin", "short")));

        assertEquals("AUTH_ADMIN_BOOTSTRAP_INVALID", failure.code());
    }

    private static final class InMemoryPort implements AdminBootstrapAccountPort {
        private final Map<String, Account> accountsByEmail = new HashMap<>();
        private final Map<String, Account> accountsByUsername = new HashMap<>();

        @Override
        public Optional<Account> findByEmailNormalized(String emailNormalized) {
            return Optional.ofNullable(accountsByEmail.get(emailNormalized));
        }

        @Override
        public Optional<Account> findByUsernameNormalized(String usernameNormalized) {
            return Optional.ofNullable(accountsByUsername.get(usernameNormalized));
        }

        @Override
        public Account save(Account account) {
            accountsByEmail.put(account.emailNormalized(), account);
            if (account.usernameNormalized() != null) {
                accountsByUsername.put(account.usernameNormalized(), account);
            }
            return account;
        }

        private Account byEmail(String email) {
            return accountsByEmail.get(email);
        }
    }
}
