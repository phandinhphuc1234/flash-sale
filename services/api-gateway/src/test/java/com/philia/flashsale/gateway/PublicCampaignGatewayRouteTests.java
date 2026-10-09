package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PublicCampaignGatewayRouteTests {

    private static final HttpServer DOWNSTREAM = startDownstream();

    @Autowired RouteLocator routes;
    @Autowired WebTestClient client;

    @DynamicPropertySource
    static void campaignUrl(DynamicPropertyRegistry registry) {
        registry.add("CAMPAIGN_SERVICE_URL", () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stop() { DOWNSTREAM.stop(0); }

    @Test
    void publicCampaignRouteIsConfiguredAndAnonymous() {
        assertThat(routes.getRoutes().map(Route::getId).collectList().block()).contains("campaign-public");
        client.get().uri("/api/v1/campaigns?page=0&size=12").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.downstream").isEqualTo("campaign");
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/api/v1/campaigns", PublicCampaignGatewayRouteTests::respond);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        byte[] response = "{\"downstream\":\"campaign\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(200, response.length);
        try (exchange; var body = exchange.getResponseBody()) { body.write(response); }
    }
}
