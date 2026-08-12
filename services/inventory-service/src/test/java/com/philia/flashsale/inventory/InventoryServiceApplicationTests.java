package com.philia.flashsale.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class InventoryServiceApplicationTests {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("inventory_db")
                    .withUsername("inventory")
                    .withPassword("inventory");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Test
    void contextLoads() {
    }

    @Test
    void refactorPreservesTheApprovedHttpRoutes() {
        assertRoute("/api/v1/admin/inventory/{variantId}", RequestMethod.GET);
        assertRoute("/api/v1/admin/inventory/{variantId}/adjustments", RequestMethod.POST);
        assertRoute("/api/v1/admin/inventory/{variantId}/movements", RequestMethod.GET);
        assertRoute("/internal/v1/campaign-stock-allocations", RequestMethod.POST);
        assertRoute("/internal/v1/campaign-stock-allocations/{requestId}/release", RequestMethod.POST);
        assertRoute("/internal/v1/campaign-stock-allocations/{requestId}/reconcile", RequestMethod.POST);
    }

    private void assertRoute(String path, RequestMethod method) {
        boolean present = handlerMapping.getHandlerMethods().keySet().stream()
                .anyMatch(mapping -> mapping.getPatternValues().contains(path)
                        && mapping.getMethodsCondition().getMethods().contains(method));
        assertTrue(present, () -> method + " " + path + " must remain mapped");
    }
}
