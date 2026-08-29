package com.philia.flashsale.campaign.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Generates Campaign's opt-in local OpenAPI document; business routes remain service-owned. */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class CampaignOpenApiConfiguration {

    @Bean
    OpenAPI campaignOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flash Sale Campaign API")
                        .version("v1")
                        .description("Campaign administration, recovery snapshot, and outbox recovery operations."))
                .components(new Components().addSecuritySchemes("bearerAuth", bearerScheme()));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
    }
}
