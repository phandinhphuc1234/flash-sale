package com.philia.flashsale.authentication.account.application.registration;

/** Outbound password-encoding capability; the application does not know the hashing provider. */
public interface EncodePasswordPort {
    String encode(String rawPassword);
}
