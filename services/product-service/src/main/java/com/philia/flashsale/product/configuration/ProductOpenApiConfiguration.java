package com.philia.flashsale.product.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Generates Product's opt-in local OpenAPI document without changing catalog authorization. */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class ProductOpenApiConfiguration {

    @Bean
    OpenAPI productOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flash Sale Product API")
                        .version("v1")
                        .description("Public catalog, catalog administration, and internal campaign validation."))
                .components(new Components().addSecuritySchemes("bearerAuth", bearerScheme()));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
    }
}
