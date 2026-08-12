package com.philia.flashsale.authentication.session.domain;

/** Lifecycle states for a login session and its refresh-token chain. */
public enum LoginSessionStatus {
    ACTIVE,
    REVOKED,
    COMPROMISED
}
