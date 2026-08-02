package com.philia.flashsale.authentication.serviceclient.application;

public interface ServiceClientSecretVerifierPort {
    boolean matches(String rawSecret, String encodedSecret);
}
