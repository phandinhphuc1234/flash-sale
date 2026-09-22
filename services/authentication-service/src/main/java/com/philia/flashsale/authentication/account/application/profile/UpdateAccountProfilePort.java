package com.philia.flashsale.authentication.account.application.profile;

import java.util.Optional;
import java.util.UUID;

import com.philia.flashsale.authentication.account.domain.Account;

/** Persistence capability required by the authenticated profile update use case. */
public interface UpdateAccountProfilePort {
    Optional<Account> findById(UUID accountId);
    Optional<Account> findByUsernameNormalized(String usernameNormalized);
    Account save(Account account);
}
