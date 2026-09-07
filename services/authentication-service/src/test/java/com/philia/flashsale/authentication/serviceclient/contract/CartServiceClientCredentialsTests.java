package com.philia.flashsale.authentication.serviceclient.contract;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.philia.flashsale.authentication.configuration.ServiceClientsProperties;
import com.philia.flashsale.authentication.serviceclient.application.ProvisionServiceClientService;
import com.philia.flashsale.authentication.serviceclient.adapter.in.oauth.ServiceClientBootstrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class CartServiceClientCredentialsTests {

    @Test
    void provisionsCartWithOnlyTheVariantDisplayScope() throws Exception {
        ProvisionServiceClientService provisioner = mock(ProvisionServiceClientService.class);
        ServiceClientsProperties properties = new ServiceClientsProperties(
                new ServiceClientsProperties.Client("campaign-service", "campaign-secret", List.of("catalog.read")),
                new ServiceClientsProperties.Client("flashsale-service", "flashsale-secret", List.of("inventory.read")),
                new ServiceClientsProperties.Client("cart-service", "cart-secret",
                        List.of("catalog.variant-display.read")),
                new ServiceClientsProperties.Client("order-service", "order-secret",
                        List.of("cart.checkout-snapshot.read")));

        new ServiceClientBootstrapper(provisioner, properties)
                .run(new DefaultApplicationArguments());

        verify(provisioner).provisionIfConfigured(
                "cart-service", "cart-secret", List.of("catalog.variant-display.read"));
    }
}
