package com.philia.flashsale.authentication.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flashsale.auth.service-clients")
public record ServiceClientsProperties(Client campaign, Client flashsale, Client cart, Client order) {
    public record Client(String clientId, String clientSecret, List<String> allowedScopes) {
        public Client { allowedScopes = allowedScopes == null ? List.of() : List.copyOf(allowedScopes); }
    }
}
