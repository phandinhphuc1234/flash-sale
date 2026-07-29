package com.philia.flashsale.authentication.account.application.registration;

import java.time.Clock;
import java.time.Instant;

import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;

/** Registration orchestration and account-creation policy without transport or persistence types. */
public class RegisterAccountService implements RegisterAccountUseCase {
    private final RegisterAccountPort accountPort;
    private final EncodePasswordPort passwordPort;
    private final Clock clock;

    public RegisterAccountService(RegisterAccountPort accountPort, EncodePasswordPort passwordPort, Clock clock) {
        this.accountPort = accountPort;
        this.passwordPort = passwordPort;
        this.clock = clock;
    }

    @Override
    public RegisterAccountResult register(RegisterAccountCommand command) {
        if (command == null) throw new AccountFailure("AUTH_VALIDATION_FAILED", "Request is required");
        validatePassword(command.password());
        String emailNormalized = Account.normalizeRequired(command.email(), "email");
        String usernameNormalized = Account.normalizeOptional(command.username());
        if (accountPort.findByEmailNormalized(emailNormalized).isPresent()
                || (usernameNormalized != null && accountPort.findByUsernameNormalized(usernameNormalized).isPresent())) {
            throw new AccountFailure("AUTH_ACCOUNT_ALREADY_EXISTS", "Account already exists");
        }
        String encoded = passwordPort.encode(command.password());
        Account account = Account.register(command.email(), command.username(), encoded, Instant.now(clock));
        try {
            return RegisterAccountResult.from(accountPort.save(account));
        } catch (RuntimeException exception) {
            throw new AccountFailure("AUTH_ACCOUNT_ALREADY_EXISTS", "Account already exists");
        }
    }

    private void validatePassword(String password) {
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (length < 12 || length > 128) throw new AccountFailure("AUTH_VALIDATION_FAILED", "Password length is invalid");
    }
}
