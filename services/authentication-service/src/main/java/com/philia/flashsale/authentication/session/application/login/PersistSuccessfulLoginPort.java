package com.philia.flashsale.authentication.session.application.login;

import java.time.Instant;
import com.philia.flashsale.authentication.account.domain.Account;
import java.util.UUID;

/** Outbound atomic-success capability for account, session, and current refresh state. */
public interface PersistSuccessfulLoginPort {
    UUID persist(Account account, String refreshHash, String deviceName, String userAgent, String directIp, Instant now);
}
