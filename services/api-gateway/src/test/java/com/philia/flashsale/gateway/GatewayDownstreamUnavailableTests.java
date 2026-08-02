package com.philia.flashsale.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GatewayDownstreamUnavailableTests {

    private static final int CLOSED_PORT = reserveThenReleaseLocalPort();
    private static final String INTERNAL_DESTINATION = "127.0.0.1:" + CLOSED_PORT;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void routeToClosedPort(DynamicPropertyRegistry registry) {
        registry.add("PRODUCT_SERVICE_URL", () -> "http://" + INTERNAL_DESTINATION);
    }

    @Test
    void preservesValidTraceWhenSelectedDownstreamCannotBeReached() throws Exception {
        JsonNode body = unavailableResponse("trace-downstream-unavailable");

        assertExactUnavailableEnvelope(body);
        assertThat(body.path("traceId").isMissingNode()).isTrue();
        assertNoInfrastructureLeakage(body.toString());
    }

    @Test
    void generatesTraceWhenSelectedDownstreamCannotBeReached() throws Exception {
        JsonNode body = unavailableResponse(null);

        assertExactUnavailableEnvelope(body);
        assertThat(body.path("traceId").isMissingNode()).isTrue();
        assertNoInfrastructureLeakage(body.toString());
    }

    private JsonNode unavailableResponse(String traceId) throws Exception {
        WebTestClient.RequestHeadersSpec<?> request = webTestClient.get()
                .uri("/api/v1/catalog/products?source=closed-port-test");
        if (traceId != null) {
            request = request.header("X-Trace-Id", traceId);
        }

        var response = request.exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().valueMatches("X-Trace-Id", ".+")
                .expectBody()
                .returnResult();

        byte[] responseBody = response.getResponseBody();

        assertThat(responseBody).isNotNull();
        return objectMapper.readTree(responseBody);
    }

    private void assertExactUnavailableEnvelope(JsonNode body) {
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.path("errorCode").asText()).isEqualTo("DOWNSTREAM_UNAVAILABLE");
        assertThat(body.path("message").asText())
                .isEqualTo("The requested service is temporarily unavailable");
        assertThat(body.has("traceId")).isFalse();
    }

    private void assertNoInfrastructureLeakage(String responseBody) {
        assertThat(responseBody)
                .doesNotContain(INTERNAL_DESTINATION)
                .doesNotContain(Integer.toString(CLOSED_PORT))
                .doesNotContain("ConnectException")
                .doesNotContain("Connection refused");
    }

    private static int reserveThenReleaseLocalPort() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
