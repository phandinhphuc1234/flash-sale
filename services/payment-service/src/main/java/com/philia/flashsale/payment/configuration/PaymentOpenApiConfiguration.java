package com.philia.flashsale.payment.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Generates Payment's opt-in Checkout, owner-query, and Stripe webhook OpenAPI document. */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class PaymentOpenApiConfiguration {

    @Bean
    OpenAPI paymentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flash Sale Payment API")
                        .version("v1")
                        .description("Owned payment queries, hosted Checkout Sessions, and signed Stripe webhooks."))
                .components(new Components().addSecuritySchemes("bearerAuth", bearerScheme()));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
    }
}
