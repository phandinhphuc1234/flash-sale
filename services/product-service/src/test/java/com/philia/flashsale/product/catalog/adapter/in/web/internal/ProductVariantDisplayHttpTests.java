package com.philia.flashsale.product.catalog.adapter.in.web.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.product.catalog.application.port.in.LookupVariantDisplaysUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Contract coverage for the Cart-only Product batch endpoint and its dedicated security chain. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductVariantDisplayHttpTests {

    private static final String INTERNAL_AUDIENCE = "flash-sale-internal-api";
    private static final String CART_SUBJECT = "cart-service";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("product_db")
            .withUsername("product")
            .withPassword("product");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LookupVariantDisplaysUseCase lookupVariantDisplays;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Test
    void acceptsCartMachineIdentityAndReturnsVersionedEnvelope() throws Exception {
        UUID variantId = UUID.randomUUID();
        when(lookupVariantDisplays.lookup(List.of(variantId)))
                .thenReturn(List.of(new com.philia.flashsale.product.catalog.application.result.VariantDisplayResult(
                        variantId, true, true, UUID.randomUUID(), "shirt", "Shirt", "Black / M",
                        "SHIRT-BLK-M", new java.math.BigDecimal("299000.0000"), "VND",
                        "https://cdn.example.test/shirt.webp")));

        mockMvc.perform(post("/internal/v1/catalog/variants/display-details")
                        .with(jwt().jwt(token -> token.subject(CART_SUBJECT)
                                .audience(List.of(INTERNAL_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_catalog.variant-display.read")))
                        .header("X-Trace-Id", "trace-cart-product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantIds\":[\"" + variantId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-cart-product"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.variants[0].variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.data.variants[0].basePrice").value("299000.0000"));
    }

    @Test
    void rejectsWrongMachineScopeWithoutInvokingUseCase() throws Exception {
        mockMvc.perform(post("/internal/v1/catalog/variants/display-details")
                        .with(jwt().jwt(token -> token.subject(CART_SUBJECT)
                                .audience(List.of(INTERNAL_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_catalog.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_VARIANT_DISPLAY_SCOPE_REQUIRED"));
    }

    @Test
    void rejectsWrongMachineSubjectWithoutInvokingUseCase() throws Exception {
        mockMvc.perform(post("/internal/v1/catalog/variants/display-details")
                        .with(jwt().jwt(token -> token.subject("campaign-service")
                                .audience(List.of(INTERNAL_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_catalog.variant-display.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_VARIANT_DISPLAY_SCOPE_REQUIRED"));
    }

    @Test
    void rejectsMissingMachineAuthentication() throws Exception {
        mockMvc.perform(post("/internal/v1/catalog/variants/display-details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantIds\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }
}
