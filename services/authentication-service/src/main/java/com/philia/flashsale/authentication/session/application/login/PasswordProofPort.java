package com.philia.flashsale.authentication.session.application.login;

/** Outbound capability for constant-shape password verification. */
public interface PasswordProofPort {
    boolean matches(String rawPassword, String encodedPassword);

    /** Performs the same adaptive proof when no account hash is available. */
    void verifyDummy(String rawPassword);
}
