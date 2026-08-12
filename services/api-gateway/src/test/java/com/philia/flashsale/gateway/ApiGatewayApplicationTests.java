package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoads() {
    }

    @Test
    void productCatalogRouteIsConfigured() {
        assertThat(routeLocator.getRoutes()
                        .map(Route::getId)
                        .collectList()
                        .block(Duration.ofSeconds(5)))
                .contains("product-catalog", "product-catalog-admin");
    }
}
