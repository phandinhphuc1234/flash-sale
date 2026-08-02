package com.philia.flashsale.authentication.serviceclient.application;

import java.util.List;

/** Provisions fixed machine identities without ever replacing an existing secret. */
public class ProvisionServiceClientService {
    private final ProvisionServiceClientPort provisioner;
    public ProvisionServiceClientService(ProvisionServiceClientPort provisioner) { this.provisioner = provisioner; }
    public void provisionIfConfigured(String clientId, String rawSecret, List<String> scopes) {
        if (clientId != null && !clientId.isBlank() && rawSecret != null && !rawSecret.isBlank()) {
            provisioner.provisionIfAbsent(clientId, rawSecret, scopes);
        }
    }
}
