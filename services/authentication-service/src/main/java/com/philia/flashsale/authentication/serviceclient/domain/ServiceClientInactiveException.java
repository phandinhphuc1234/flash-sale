package com.philia.flashsale.authentication.serviceclient.domain;

public class ServiceClientInactiveException extends RuntimeException {
    public ServiceClientInactiveException(String clientId) {
        super("Service client is inactive: " + clientId);
    }
}
