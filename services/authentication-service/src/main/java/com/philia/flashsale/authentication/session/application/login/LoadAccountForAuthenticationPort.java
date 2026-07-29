package com.philia.flashsale.authentication.session.application.login;

import java.util.Optional;
import com.philia.flashsale.authentication.account.domain.Account;

/** Outbound lookup capability used by the login use case. */
public interface LoadAccountForAuthenticationPort {
    Optional<Account> load(String normalizedLogin);
}
