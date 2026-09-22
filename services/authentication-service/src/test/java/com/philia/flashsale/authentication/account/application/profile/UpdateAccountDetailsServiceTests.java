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

class UpdateAccountDetailsServiceTests {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void updatesContactDetailsWithoutChangingEmailOrRole() {
        Account account = Account.register("user@example.com", "old-name", "encoded", NOW);
        FakePort port = new FakePort(account);
        UpdateAccountDetailsService service = new UpdateAccountDetailsService(
                port, Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));

        AccountProfile profile = service.update(new UpdateAccountDetailsCommand(account.id(), "new-name", true,
                "Phuc Nguyen", true, "+84901234567", true, "Thu Duc", true));

        assertEquals("new-name", profile.username());
        assertEquals("Phuc Nguyen", profile.fullName());
        assertEquals("+84901234567", profile.phone());
        assertEquals("Thu Duc", profile.address());
        assertEquals("user@example.com", profile.email());
        assertEquals("ROLE_USER", profile.authorities().getFirst());
    }

    @Test
    void rejectsEmptyPatch() {
        Account account = Account.register("user@example.com", "old-name", "encoded", NOW);
        UpdateAccountDetailsService service = new UpdateAccountDetailsService(new FakePort(account), Clock.systemUTC());

        AccountFailure failure = assertThrows(AccountFailure.class,
                () -> service.update(new UpdateAccountDetailsCommand(account.id(), null, false,
                        null, false, null, false, null, false)));

        assertEquals("AUTH_VALIDATION_FAILED", failure.code());
    }

    private static final class FakePort implements UpdateAccountProfilePort {
        private final Map<UUID, Account> accountsById = new HashMap<>();
        private final Map<String, Account> accounts = new HashMap<>();

        private FakePort(Account account) {
            accountsById.put(account.id(), account);
            accounts.put(account.usernameNormalized(), account);
        }

        @Override public Optional<Account> findById(UUID accountId) { return Optional.ofNullable(accountsById.get(accountId)); }
        @Override public Optional<Account> findByUsernameNormalized(String usernameNormalized) { return Optional.ofNullable(accounts.get(usernameNormalized)); }
        @Override public Account save(Account account) {
            accountsById.put(account.id(), account);
            accounts.put(account.usernameNormalized(), account);
            return account;
        }
    }
}
