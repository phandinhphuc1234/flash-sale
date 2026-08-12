package com.philia.flashsale.authentication.session.application.refresh;

import java.time.Instant;
import java.util.UUID;
import com.philia.flashsale.authentication.account.domain.Account;

/** Outbound capability for issuing a replacement access token after rotation commits. */
public interface IssueRefreshedCredentialsPort {
    String issue(Account account, UUID sessionId, Instant issuedAt);
}
