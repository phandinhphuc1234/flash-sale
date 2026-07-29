package com.philia.flashsale.authentication.session.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoginSessionTests {
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void activityIsRecordedOnlyWhileActive() {
        LoginSession session = LoginSession.create(UUID.randomUUID(), "browser", "ua", "127.0.0.1",
                CREATED, CREATED.plusSeconds(3600));

        session.recordActivity(CREATED.plusSeconds(5));
        assertEquals(CREATED.plusSeconds(5), session.lastActivityAt());
        session.revoke("logout", CREATED.plusSeconds(6));
        assertThrows(SessionFailure.class, () -> session.recordActivity(CREATED.plusSeconds(7)));
    }

    @Test
    void refreshCredentialIsCurrentOnlyBeforeUseOrRevocation() {
        RefreshCredential credential = new RefreshCredential(UUID.randomUUID(), UUID.randomUUID(), "hash",
                null, null, CREATED, CREATED.plusSeconds(60), null, null, null);
        assertEquals(true, credential.isCurrent(CREATED));
        assertEquals(false, credential.isCurrent(CREATED.plusSeconds(61)));
    }
}
