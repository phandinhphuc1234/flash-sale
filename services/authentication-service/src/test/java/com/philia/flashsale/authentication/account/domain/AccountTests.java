package com.philia.flashsale.authentication.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountTests {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void registrationNormalizesIdentityAndForcesShopperRole() {
        Account account = Account.register(" User@Example.COM ", " Shopper ", "argon-hash", NOW);

        assertEquals("user@example.com", account.emailNormalized());
        assertEquals("shopper", account.usernameNormalized());
        assertEquals(AccountRole.ROLE_USER, account.role());
        assertEquals(AccountStatus.ACTIVE, account.status());
    }

    @Test
    void blankRequiredIdentityIsRejected() {
        assertThrows(AccountFailure.class, () -> Account.register(" ", null, "hash", NOW));
    }
}
