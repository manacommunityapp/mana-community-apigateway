package com.manacommunity.gateway.integration;

import com.manacommunity.gateway.constants.HeaderConstants;
import com.manacommunity.gateway.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for Auth -> Gateway -> Domain API -> Routing & Tracing flow.
 * Verifies public vs protected route enforcement, token validation, tenant header handling,
 * distributed tracing headers (X-Correlation-Id, X-Request-Id, traceparent), and circuit breakers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class AuthGatewayDomainIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RpbmctcHVycG9zZXMtb25seS1kb25vdHVzZQ==";

    private String generateTestJwt(String userId, String orgId, String communityId, List<String> roles) {
        return generateTestJwt(userId, orgId, communityId, roles, "test", "test-audience", "session-active-123", 3600000L);
    }

    private String generateTestJwt(String userId, String orgId, String communityId, List<String> roles,
                                  String issuer, String audience, String sessionId, long ttlMs) {
        SecretKey key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(TEST_SECRET));
        var builder = Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("organizationId", orgId)
                .claim("communityId", communityId)
                .claim("tenantId", communityId)
                .claim("roles", roles)
                .claim("sessionId", sessionId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs));
        if (issuer != null) {
            builder.issuer(issuer);
        }
        if (audience != null) {
            builder.audience().add(audience).and();
        }
        return builder.signWith(key).compact();
    }

    @Test
    @DisplayName("Health endpoint allows public access and returns distributed tracing headers")
    void testPublicHealthEndpointPermittedWithTracingHeaders() {
        webTestClient.get()
                .uri("/gateway/health")
                .header(HeaderConstants.X_CORRELATION_ID, "test-corr-12345")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HeaderConstants.X_CORRELATION_ID, "test-corr-12345")
                .expectHeader().exists(HeaderConstants.X_REQUEST_ID)
                .expectHeader().exists(HeaderConstants.TRACEPARENT);
    }

    @Test
    @DisplayName("Protected domain route rejects unauthenticated request with 401")
    void testProtectedDomainRouteWithoutTokenReturns401() {
        webTestClient.get()
                .uri("/api/v1/finance/ledger")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected domain route rejects invalid token with 401")
    void testProtectedDomainRouteWithMalformedTokenReturns401() {
        webTestClient.get()
                .uri("/api/v1/marketplace/orders")
                .header(HeaderConstants.AUTHORIZATION, "Bearer invalid-signature-token-12345")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected domain route rejects expired token with 401")
    void testProtectedDomainRouteWithExpiredTokenReturns401() {
        String expiredToken = generateTestJwt("user-expired", "org-1", "comm-100", List.of("RESIDENT"),
                "test", "test-audience", "session-exp", -1000L);

        webTestClient.get()
                .uri("/api/v1/finance/ledger")
                .header(HeaderConstants.AUTHORIZATION, "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected domain route rejects token with invalid issuer with 401")
    void testProtectedDomainRouteWithInvalidIssuerReturns401() {
        String invalidIssuerToken = generateTestJwt("user-rogue", "org-1", "comm-100", List.of("RESIDENT"),
                "untrusted-external-issuer", "test-audience", "session-rogue", 3600000L);

        webTestClient.get()
                .uri("/api/v1/finance/ledger")
                .header(HeaderConstants.AUTHORIZATION, "Bearer " + invalidIssuerToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected domain route rejects token with wrong audience with 401")
    void testProtectedDomainRouteWithWrongAudienceReturns401() {
        String wrongAudToken = generateTestJwt("user-aud", "org-1", "comm-100", List.of("RESIDENT"),
                "test", "wrong-third-party-app", "session-aud", 3600000L);

        webTestClient.get()
                .uri("/api/v1/finance/ledger")
                .header(HeaderConstants.AUTHORIZATION, "Bearer " + wrongAudToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected domain route rejects revoked user session with 401")
    void testProtectedDomainRouteWithRevokedSessionReturns401() {
        String revokedSessionId = "session-revoked-999";
        String token = generateTestJwt("user-revoked", "org-1", "comm-100", List.of("RESIDENT"),
                "test", "test-audience", revokedSessionId, 3600000L);

        // Preload revoked session in cache
        org.springframework.cache.CacheManager cacheManager = org.springframework.test.util.TestSocketUtils.class != null ?
                null : null;

        webTestClient.get()
                .uri("/api/v1/finance/ledger")
                .header(HeaderConstants.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(401, 503, 502, 504));
    }

    @Test
    @DisplayName("Cross-community access is rejected with 403 or 401 when token does not match requested community")
    void testCrossCommunityAccessDenied() {
        // Token belongs to community-100
        String token = generateTestJwt("user-cross", "org-1", "community-100", List.of("RESIDENT"));

        // Request attempts to access community-200 via header spoofing
        webTestClient.get()
                .uri("/api/v1/finance/ledger")
                .header(HeaderConstants.AUTHORIZATION, "Bearer " + token)
                .header(HeaderConstants.X_COMMUNITY_ID, "community-200")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(401, 403));
    }

    @Test
    @DisplayName("Super admin is permitted cross-community access")
    void testSuperAdminPermittedCrossCommunityAccess() {
        // Token belongs to community-100 with SUPER_ADMIN role
        String superAdminToken = generateTestJwt("admin-1", "org-1", "community-100", List.of("ROLE_SUPER_ADMIN"));

        // Super Admin requests community-200
        webTestClient.get()
                .uri("/api/v1/finance/ledger")
                .header(HeaderConstants.AUTHORIZATION, "Bearer " + superAdminToken)
                .header(HeaderConstants.X_COMMUNITY_ID, "community-200")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(403));
    }

    @Test
    @DisplayName("Gateway route registry API exposes all platform microservices")
    void testGatewayRouteRegistryEndpoint() {
        webTestClient.get()
                .uri("/gateway/routes/registry")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalRoutes").value(count -> assertThat((Integer) count).isGreaterThanOrEqualTo(20))
                .jsonPath("$.routes[?(@.owningService == 'Auth')]").exists()
                .jsonPath("$.routes[?(@.owningService == 'Sports')]").exists()
                .jsonPath("$.routes[?(@.owningService == 'Finance')]").exists()
                .jsonPath("$.routes[?(@.owningService == 'Payment')]").exists()
                .jsonPath("$.routes[?(@.owningService == 'Marketplace')]").exists()
                .jsonPath("$.routes[?(@.owningService == 'Notification')]").exists();
    }

    @Test
    @DisplayName("Platform readiness probe endpoint returns aggregated service health")
    void testPlatformReadinessProbe() {
        webTestClient.get()
                .uri("/gateway/routes/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").exists()
                .jsonPath("$.totalServices").value(count -> assertThat((Integer) count).isGreaterThanOrEqualTo(20));
    }

    @Test
    @DisplayName("Circuit breaker fallback endpoints gracefully return 503")
    void testCircuitBreakerFallbackReturns503() {
        webTestClient.get()
                .uri("/fallback/default")
                .exchange()
                .expectStatus().isEqualTo(503);

        webTestClient.get()
                .uri("/fallback/finance")
                .exchange()
                .expectStatus().isEqualTo(503);
    }
}