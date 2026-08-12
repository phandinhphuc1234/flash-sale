package com.philia.flashsale.authentication.account.domain;

/** Lifecycle states used when deciding whether an account can authenticate. */
public enum AccountStatus {
    ACTIVE,
    LOCKED,
    DISABLED
}
