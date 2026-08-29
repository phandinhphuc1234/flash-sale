package com.philia.flashsale.authentication.serviceclient.adapter.in.oauth;

import com.philia.flashsale.authentication.configuration.ServiceClientsProperties;
import com.philia.flashsale.authentication.serviceclient.application.ProvisionServiceClientService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Bootstraps the approved fixed machine identities without replacing existing secrets. */
@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true")
public final class ServiceClientBootstrapper implements ApplicationRunner {

    private final ProvisionServiceClientService provisioner;
    private final ServiceClientsProperties properties;

    public ServiceClientBootstrapper(
            ProvisionServiceClientService provisioner,
            ServiceClientsProperties properties) {
        this.provisioner = provisioner;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        provision(properties.campaign());
        provision(properties.flashsale());
        provision(properties.cart());
    }

    private void provision(ServiceClientsProperties.Client client) {
        if (client != null) {
            provisioner.provisionIfConfigured(
                    client.clientId(), client.clientSecret(), client.allowedScopes());
        }
    }
}
