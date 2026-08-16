package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

/** Contract-level route checks for the read-only public Order boundary. */
@SpringBootTest
class OrderGatewayRouteConfigurationTests {
    @Autowired
    private RouteLocator routes;

    @Test
    void orderPublicRouteTargetsOrderServiceAndIsPresent() {
        Route route = routes.getRoutes().filter(candidate -> candidate.getId().equals("order-public"))
                .next().block(Duration.ofSeconds(5));

        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://order-service:8080");
    }
}
