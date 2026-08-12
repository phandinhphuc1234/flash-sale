package com.philia.flashsale.authentication.serviceclient.application;

import java.util.List;

public interface ProvisionServiceClientPort {
    void provisionIfAbsent(String clientId, String rawSecret, List<String> scopes);
}
