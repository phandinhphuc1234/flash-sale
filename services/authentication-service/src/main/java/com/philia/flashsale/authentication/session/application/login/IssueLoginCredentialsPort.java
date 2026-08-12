package com.philia.flashsale.authentication.session.application.login;

import java.time.Instant;
import java.util.UUID;
import com.philia.flashsale.authentication.account.domain.Account;

/** Outbound capability for issuing an access token after durable login success. */
public interface IssueLoginCredentialsPort {
    String issueAccessToken(Account account, UUID sessionId, Instant issuedAt);
}
