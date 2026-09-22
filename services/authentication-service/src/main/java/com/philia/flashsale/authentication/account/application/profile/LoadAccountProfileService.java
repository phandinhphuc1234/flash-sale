package com.philia.flashsale.authentication.account.application.profile;

import java.util.UUID;

import com.philia.flashsale.authentication.account.domain.AccountFailure;

/** Maps the Authentication account aggregate to a safe, read-only profile result. */
public class LoadAccountProfileService implements LoadAccountProfileUseCase {
    private final LoadAccountProfilePort accountPort;

    public LoadAccountProfileService(LoadAccountProfilePort accountPort) {
        this.accountPort = accountPort;
    }

    @Override
    public AccountProfile load(UUID accountId) {
        if (accountId == null) {
            throw new AccountFailure("AUTH_ACCOUNT_NOT_FOUND", "Account was not found");
        }
        var account = accountPort.findById(accountId)
                .orElseThrow(() -> new AccountFailure("AUTH_ACCOUNT_NOT_FOUND", "Account was not found"));
        return AccountProfile.from(account);
    }
}
