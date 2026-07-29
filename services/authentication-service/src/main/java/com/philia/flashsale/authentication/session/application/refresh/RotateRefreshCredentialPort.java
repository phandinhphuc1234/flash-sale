package com.philia.flashsale.authentication.session.application.refresh;

import java.time.Instant;
import com.philia.flashsale.authentication.account.domain.Account;

/** Outbound locked-rotation capability owned by the persistence adapter. */
public interface RotateRefreshCredentialPort {
    RotationOutcome rotate(String currentHash, String successorHash, Instant now);

    sealed interface RotationOutcome permits RefreshRotation, RefreshReuseDetected { }

    record RefreshRotation(Account account, java.util.UUID sessionId, Instant successorExpiresAt)
            implements RotationOutcome { }

    /** A normal return lets the persistence transaction commit compromise state before HTTP maps 401. */
    record RefreshReuseDetected() implements RotationOutcome { }
}
