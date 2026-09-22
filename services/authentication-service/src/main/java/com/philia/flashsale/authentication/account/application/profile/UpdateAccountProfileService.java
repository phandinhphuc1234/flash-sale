package com.philia.flashsale.authentication.account.application.profile;

import java.time.Clock;

import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;

/** Updates only the authenticated account username; email and security fields remain immutable here. */
public class UpdateAccountProfileService implements UpdateAccountProfileUseCase {
    private final UpdateAccountProfilePort accountPort;
    private final Clock clock;

    public UpdateAccountProfileService(UpdateAccountProfilePort accountPort, Clock clock) {
        this.accountPort = accountPort;
        this.clock = clock;
    }

    @Override
    public AccountProfile update(UpdateAccountProfileCommand command) {
        if (command == null || command.accountId() == null) {
            throw new AccountFailure("AUTH_ACCOUNT_NOT_FOUND", "Account was not found");
        }
        String normalizedUsername = Account.normalizeRequired(command.username(), "username");
        Account account = accountPort.findById(command.accountId())
                .orElseThrow(() -> new AccountFailure("AUTH_ACCOUNT_NOT_FOUND", "Account was not found"));
        accountPort.findByUsernameNormalized(normalizedUsername)
                .filter(existing -> !existing.id().equals(account.id()))
                .ifPresent(existing -> {
                    throw new AccountFailure("AUTH_ACCOUNT_ALREADY_EXISTS", "Username is already in use");
                });
        account.updateUsername(command.username(), clock.instant());
        return AccountProfile.from(accountPort.save(account));
    }
}
