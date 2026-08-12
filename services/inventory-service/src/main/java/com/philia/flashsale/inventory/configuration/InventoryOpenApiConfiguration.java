package com.philia.flashsale.inventory.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Framework configuration for the Inventory Service's generated OpenAPI contract. */
@Configuration
public class InventoryOpenApiConfiguration {

    @Bean
    OpenAPI inventoryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flash Sale Inventory Service API")
                        .version("0.1.0")
                        .description("Admin inventory operations and internal campaign allocation lifecycle."))
                .addTagsItem(new Tag().name("Admin inventory")
                        .description("Privileged inventory reads and physical stock adjustments."))
                .addTagsItem(new Tag().name("Internal allocation")
                        .description("Service-to-service campaign allocation lifecycle; never expose through the public Gateway."))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Use INVENTORY_ADMIN for admin endpoints or SCOPE_INVENTORY_WRITE for internal allocation endpoints.")));
    }
}
