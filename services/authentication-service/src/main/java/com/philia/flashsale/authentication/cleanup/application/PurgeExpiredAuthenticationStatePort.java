package com.philia.flashsale.authentication.cleanup.application;

import java.time.Instant;

/** Persistence boundary for deleting state that is no longer needed for security decisions. */
/** Outbound deletion capability for retained refresh and inactive-session rows. */
public interface PurgeExpiredAuthenticationStatePort {
    CleanupResult purge(Instant now, Instant retentionCutoff);

    record CleanupResult(int refreshTokensDeleted, int sessionsDeleted) { }
}
