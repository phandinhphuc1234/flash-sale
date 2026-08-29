package com.philia.flashsale.authentication.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

/** Generates Authentication's opt-in local OpenAPI document, including its framework-owned token endpoint. */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class AuthenticationOpenApiConfiguration {

    @Bean
    OpenAPI authenticationOpenApi() {
        var tokenForm = new ObjectSchema()
                .addProperty("grant_type", new StringSchema().example("client_credentials"))
                .addProperty("scope", new StringSchema().example("inventory.campaign.allocate"));
        var tokenOperation = new Operation()
                .addTagsItem("Service authentication")
                .summary("Issue an internal client-credentials access token")
                .description("Supported for approved machine clients only; client credentials use HTTP Basic authentication.")
                .addSecurityItem(new SecurityRequirement().addList("clientBasic"))
                .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                        .required(true)
                        .content(new Content().addMediaType(
                                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                                new io.swagger.v3.oas.models.media.MediaType().schema(tokenForm))))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Access token issued"))
                        .addApiResponse("400", new ApiResponse().description("Unsupported or invalid token request"))
                        .addApiResponse("401", new ApiResponse().description("Invalid client credentials")));

        return new OpenAPI()
                .info(new Info()
                        .title("Flash Sale Authentication API")
                        .version("v1")
                        .description("Shopper sessions, JWT trust publication, and internal client credentials."))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme())
                        .addSecuritySchemes("clientBasic", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")))
                .path("/oauth2/token", new PathItem().post(tokenOperation));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }
}
