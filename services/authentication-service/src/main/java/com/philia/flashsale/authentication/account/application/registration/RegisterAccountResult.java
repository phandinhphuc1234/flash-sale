package com.philia.flashsale.authentication.account.application.registration;

import java.util.UUID;

import com.philia.flashsale.authentication.account.domain.Account;

/** Safe registration output; it intentionally excludes credentials and hashes. */
public record RegisterAccountResult(UUID userId, String email, String username, String role, String status) {
    public static RegisterAccountResult from(Account account) {
        return new RegisterAccountResult(account.id(), account.email(), account.username(), account.role().name(), account.status().name());
    }
}
