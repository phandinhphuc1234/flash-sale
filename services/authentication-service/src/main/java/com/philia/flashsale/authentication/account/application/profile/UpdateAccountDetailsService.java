package com.philia.flashsale.authentication.account.application.profile;

import java.time.Clock;

import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;

/** Updates visible contact fields without changing identity or security-owned fields. */
public class UpdateAccountDetailsService implements UpdateAccountDetailsUseCase {
    private final UpdateAccountProfilePort accountPort;
    private final Clock clock;

    public UpdateAccountDetailsService(UpdateAccountProfilePort accountPort, Clock clock) {
        this.accountPort = accountPort;
        this.clock = clock;
    }

    @Override
    public AccountProfile update(UpdateAccountDetailsCommand command) {
        if (command == null || command.accountId() == null || !command.hasEditableField()) {
            throw new AccountFailure("AUTH_VALIDATION_FAILED", "At least one profile field is required");
        }
        Account account = accountPort.findById(command.accountId())
                .orElseThrow(() -> new AccountFailure("AUTH_ACCOUNT_NOT_FOUND", "Account was not found"));
        if (command.usernameProvided()) {
            String normalizedUsername = Account.normalizeRequired(command.username(), "username");
            accountPort.findByUsernameNormalized(normalizedUsername)
                    .filter(existing -> !existing.id().equals(account.id()))
                    .ifPresent(existing -> {
                        throw new AccountFailure("AUTH_ACCOUNT_ALREADY_EXISTS", "Username is already in use");
                    });
        }
        account.updateProfileFields(command.usernameProvided(), command.username(),
                command.fullNameProvided(), command.fullName(),
                command.phoneProvided(), command.phone(),
                command.addressProvided(), command.address(), clock.instant());
        return AccountProfile.from(accountPort.save(account));
    }
}
