package com.philia.flashsale.authentication.serviceclient.domain;

public class ServiceClientScopeException extends RuntimeException {
    public ServiceClientScopeException(String clientId) {
        super("Requested scope is not allowed for service client: " + clientId);
    }
}
