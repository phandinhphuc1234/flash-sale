package com.philia.flashsale.authentication.account.application.bootstrap;

import java.util.Optional;

import com.philia.flashsale.authentication.account.domain.Account;

/** Outbound capability for the explicit, operator-only administrator bootstrap. */
public interface AdminBootstrapAccountPort {
    Optional<Account> findByEmailNormalized(String emailNormalized);

    Optional<Account> findByUsernameNormalized(String usernameNormalized);

    Account save(Account account);
}
