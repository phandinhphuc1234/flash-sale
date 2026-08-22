package com.philia.flashsale.authentication.account.application.bootstrap;

import java.time.Clock;
import java.time.Instant;

import com.philia.flashsale.authentication.account.application.registration.EncodePasswordPort;
import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;
import com.philia.flashsale.authentication.account.domain.AccountRole;
import com.philia.flashsale.authentication.account.domain.AccountStatus;

/**
 * Privileged account provisioning policy. It is reachable only from the opt-in bootstrap adapter,
 * never from public registration or an HTTP controller.
 */
public final class BootstrapAdminAccountService implements BootstrapAdminAccountUseCase {
    private final AdminBootstrapAccountPort accountPort;
    private final EncodePasswordPort passwordPort;
    private final Clock clock;

    public BootstrapAdminAccountService(AdminBootstrapAccountPort accountPort,
                                        EncodePasswordPort passwordPort,
                                        Clock clock) {
        this.accountPort = accountPort;
        this.passwordPort = passwordPort;
        this.clock = clock;
    }

    @Override
    public BootstrapAdminAccountResult bootstrap(BootstrapAdminAccountCommand command) {
        if (command == null) {
            throw new AccountFailure("AUTH_ADMIN_BOOTSTRAP_INVALID", "Bootstrap input is required");
        }
        validatePassword(command.password());

        String emailNormalized = Account.normalizeRequired(command.email(), "email");
        String usernameNormalized = Account.normalizeRequired(command.username(), "username");
        Account emailMatch = accountPort.findByEmailNormalized(emailNormalized).orElse(null);
        Account usernameMatch = accountPort.findByUsernameNormalized(usernameNormalized).orElse(null);

        if (emailMatch != null || usernameMatch != null) {
            if (isSameActiveAdmin(emailMatch, usernameMatch)) {
                return new BootstrapAdminAccountResult(emailMatch.id(), false);
            }
            throw new AccountFailure("AUTH_ADMIN_BOOTSTRAP_CONFLICT",
                    "Bootstrap identity is already owned by a different or non-admin account");
        }

        String encodedPassword = passwordPort.encode(command.password());
        Account account = Account.bootstrapAdmin(command.email(), command.username(), encodedPassword,
                Instant.now(clock));
        Account saved = accountPort.save(account);
        return new BootstrapAdminAccountResult(saved.id(), true);
    }

    private boolean isSameActiveAdmin(Account emailMatch, Account usernameMatch) {
        return emailMatch != null
                && usernameMatch != null
                && emailMatch.id().equals(usernameMatch.id())
                && emailMatch.role() == AccountRole.ROLE_ADMIN
                && emailMatch.status() == AccountStatus.ACTIVE;
    }

    private void validatePassword(String password) {
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (length < 12 || length > 128) {
            throw new AccountFailure("AUTH_ADMIN_BOOTSTRAP_INVALID", "Password length is invalid");
        }
    }
}
