package com.manacommunity.gateway.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpoint_returnsOk() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() {
        webTestClient.get().uri("/api/v1/resident/list")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void publicEndpoint_login_doesNotRequireAuth() {
        webTestClient.post().uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().is5xxServerError(); // 503 because no upstream service
    }

    @Test
    void fallbackEndpoint_returns503() {
        webTestClient.get().uri("/fallback/default")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void gatewayInfo_returnsOk() {
        webTestClient.get().uri("/gateway/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void swaggerUi_isAccessible() {
        webTestClient.get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection();
    }

    @Test
    void unsupportedApiVersion_returns400() {
        webTestClient.get().uri("/api/v99/resident/list")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void correlationId_isReturnedInResponse() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectHeader().exists("X-Correlation-Id");
    }
}
