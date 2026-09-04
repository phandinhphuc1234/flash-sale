package com.philia.flashsale.product.catalogquery.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.product.catalogquery.application.port.in.LookupPurchaseQuotesUseCase;
import com.philia.flashsale.product.catalogquery.application.result.PurchaseQuoteResult;
import java.math.BigDecimal;
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

/** HTTP and exact-machine-security contract coverage for Order's purchase quote endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PurchaseQuoteHttpTests {

    private static final String INTERNAL_AUDIENCE = "flash-sale-internal-api";
    private static final String ORDER_SUBJECT = "order-service";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("product_db")
            .withUsername("product")
            .withPassword("product");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LookupPurchaseQuotesUseCase lookupPurchaseQuotes;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Test
    void acceptsOrderIdentityAndReturnsTheCurrentPurchaseDecisionEnvelope() throws Exception {
        UUID variantId = UUID.randomUUID();
        when(lookupPurchaseQuotes.lookup(List.of(variantId))).thenReturn(List.of(new PurchaseQuoteResult(
                variantId, true, true, null, UUID.randomUUID(), "QUOTE-SKU", "Quote Product", "Black / M",
                new BigDecimal("179000.0000"), "VND", 7L)));

        mockMvc.perform(post("/internal/v1/catalog/variants/purchase-quotes")
                        .with(orderJwt("catalog.purchase-quote.read"))
                        .header("X-Trace-Id", "trace-order-product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantIds\":[\"" + variantId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-order-product"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Purchase quotes retrieved"))
                .andExpect(jsonPath("$.data.quotes[0].variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.data.quotes[0].unitPrice").value(179000.0000))
                .andExpect(jsonPath("$.data.quotes[0].catalogVersion").value(7));
    }

    @Test
    void rejectsWrongScopeAndBoundedInput() throws Exception {
        mockMvc.perform(post("/internal/v1/catalog/variants/purchase-quotes")
                        .with(orderJwt("catalog.variant-display.read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantIds\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_PURCHASE_QUOTE_SCOPE_REQUIRED"));

        String variantIds = java.util.stream.IntStream.range(0, 21)
                .mapToObj(ignored -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/internal/v1/catalog/variants/purchase-quotes")
                        .with(orderJwt("catalog.purchase-quote.read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantIds\":[" + variantIds + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_PURCHASE_QUOTE_VALIDATION_ERROR"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            orderJwt(String scope) {
        return jwt().jwt(token -> token.subject(ORDER_SUBJECT).audience(List.of(INTERNAL_AUDIENCE)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }
}
