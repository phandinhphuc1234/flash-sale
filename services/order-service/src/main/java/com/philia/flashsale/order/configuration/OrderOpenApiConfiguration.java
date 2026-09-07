package com.philia.flashsale.order.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Generates Order's opt-in owner read and regular checkout OpenAPI document. */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OrderOpenApiConfiguration {

    @Bean
    OpenAPI orderOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flash Sale Order API")
                        .version("v1")
                        .description("Authenticated owner order reads and regular checkout commands."))
                .components(new Components().addSecuritySchemes("bearerAuth", bearerScheme()));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
    }
}
