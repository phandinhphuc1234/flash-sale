package com.philia.flashsale.authentication.serviceclient.domain;

public class ServiceClientNotFoundException extends RuntimeException {
    public ServiceClientNotFoundException(String clientId) {
        super("Unknown service client: " + clientId);
    }
}
