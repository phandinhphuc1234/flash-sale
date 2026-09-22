package com.philia.flashsale.authentication.account.application.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;
import org.junit.jupiter.api.Test;

class UpdateAccountProfileServiceTests {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void updatesUsernameAndPreservesEmailAndRole() {
        Account account = Account.register("user@example.com", "old-name", "encoded", NOW);
        FakePort port = new FakePort(account);
        UpdateAccountProfileService service = new UpdateAccountProfileService(
                port, Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));

        AccountProfile profile = service.update(new UpdateAccountProfileCommand(account.id(), "New Name"));

        assertEquals("New Name", profile.username());
        assertEquals("user@example.com", profile.email());
        assertEquals("New Name", profile.displayName());
        assertEquals("ROLE_USER", profile.authorities().getFirst());
        assertEquals("New Name", port.saved.username());
    }

    @Test
    void rejectsUsernameOwnedByAnotherAccount() {
        Account account = Account.register("user@example.com", "old-name", "encoded", NOW);
        Account other = Account.register("other@example.com", "taken", "encoded", NOW);
        FakePort port = new FakePort(account);
        port.accounts.put(other.usernameNormalized(), other);
        UpdateAccountProfileService service = new UpdateAccountProfileService(port, Clock.systemUTC());

        AccountFailure failure = assertThrows(AccountFailure.class,
                () -> service.update(new UpdateAccountProfileCommand(account.id(), "taken")));

        assertEquals("AUTH_ACCOUNT_ALREADY_EXISTS", failure.code());
        assertEquals("old-name", account.username());
    }

    @Test
    void rejectsBlankUsername() {
        Account account = Account.register("user@example.com", "old-name", "encoded", NOW);
        UpdateAccountProfileService service = new UpdateAccountProfileService(new FakePort(account), Clock.systemUTC());

        AccountFailure failure = assertThrows(AccountFailure.class,
                () -> service.update(new UpdateAccountProfileCommand(account.id(), "  ")));

        assertEquals("AUTH_VALIDATION_FAILED", failure.code());
    }

    private static final class FakePort implements UpdateAccountProfilePort {
        private final Map<UUID, Account> accountsById = new HashMap<>();
        private final Map<String, Account> accounts = new HashMap<>();
        private Account saved;

        private FakePort(Account account) {
            accountsById.put(account.id(), account);
            if (account.usernameNormalized() != null) accounts.put(account.usernameNormalized(), account);
        }

        @Override
        public Optional<Account> findById(UUID accountId) {
            return Optional.ofNullable(accountsById.get(accountId));
        }

        @Override
        public Optional<Account> findByUsernameNormalized(String usernameNormalized) {
            return Optional.ofNullable(accounts.get(usernameNormalized));
        }

        @Override
        public Account save(Account account) {
            saved = account;
            accountsById.put(account.id(), account);
            accounts.put(account.usernameNormalized(), account);
            return account;
        }
    }
}
