package com.philia.flashsale.authentication.account.application.registration;

import java.util.Optional;

import com.philia.flashsale.authentication.account.domain.Account;

/** Outbound capability for normalized account lookup and durable creation. */
public interface RegisterAccountPort {
    Optional<Account> findByEmailNormalized(String emailNormalized);
    Optional<Account> findByUsernameNormalized(String usernameNormalized);
    Account save(Account account);
}
