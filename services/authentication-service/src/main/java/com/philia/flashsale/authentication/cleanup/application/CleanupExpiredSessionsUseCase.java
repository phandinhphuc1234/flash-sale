package com.philia.flashsale.authentication.cleanup.application;

/** Application entry point used by the scheduled cleanup adapter. */
/** Inbound application port invoked by the scheduler. */
public interface CleanupExpiredSessionsUseCase {
    PurgeExpiredAuthenticationStatePort.CleanupResult cleanup();
}
