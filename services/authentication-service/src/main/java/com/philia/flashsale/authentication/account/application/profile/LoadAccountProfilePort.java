package com.philia.flashsale.authentication.account.application.profile;

import java.util.Optional;
import java.util.UUID;

import com.philia.flashsale.authentication.account.domain.Account;

/** Authentication-owned account lookup required by the profile query. */
public interface LoadAccountProfilePort {
    Optional<Account> findById(UUID accountId);
}
