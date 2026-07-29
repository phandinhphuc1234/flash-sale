package com.philia.flashsale.authentication.account.domain;

/** Domain failure raised when an account invariant or account-authentication decision fails. */
public class AccountFailure extends RuntimeException {

    private final String code;

    public AccountFailure(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
