package com.philia.flashsale.flashsale.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Generates the opt-in Flash Sale reservation OpenAPI document. */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class FlashSaleOpenApiConfiguration {

    @Bean
    OpenAPI flashSaleOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flash Sale Reservation API")
                        .version("v1")
                        .description("Authenticated reservation creation, replay, and owner lookup."))
                .components(new Components().addSecuritySchemes("bearerAuth", bearerScheme()));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
    }
}
