package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.cache.GatewayCacheManager;
import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import com.manacommunity.gateway.security.GatewaySecurityProperties;
import com.manacommunity.gateway.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private GatewaySecurityProperties securityProperties;
    private GatewayCacheManager cacheManager;
    private ObjectProvider<GatewayCacheManager> cacheManagerProvider;
    private AuthenticationFilter authenticationFilter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        securityProperties = new GatewaySecurityProperties();
        securityProperties.setPublicPaths(List.of("/gateway/health", "/api/v*/auth/login"));

        cacheManager = mock(GatewayCacheManager.class);
        cacheManagerProvider = mock(ObjectProvider.class);
        when(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager);

        authenticationFilter = new AuthenticationFilter(jwtTokenProvider, securityProperties, cacheManagerProvider);
    }

    @Test
    @DisplayName("Missing Authorization header on protected route returns 401")
    void filter_missingAuthHeader_returnsUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/finance/ledger").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        authenticationFilter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Invalid or expired token returns 401")
    void filter_invalidToken_returnsUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/finance/ledger")
                .header(HeaderConstants.AUTHORIZATION, "Bearer invalid-or-expired-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtTokenProvider.validateToken("invalid-or-expired-token")).thenReturn(false);

        authenticationFilter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Revoked session returns 401")
    void filter_revokedSession_returnsUnauthorized() {
        String token = "valid-token-revoked-session";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/finance/ledger")
                .header(HeaderConstants.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getSessionId(token)).thenReturn("revoked-sess-123");
        when(jwtTokenProvider.getUserId(token)).thenReturn("user-1");
        when(jwtTokenProvider.getCommunityId(token)).thenReturn("comm-1");
        when(cacheManager.getCachedValue("revoked_sessions", "revoked-sess-123")).thenReturn(Mono.just("REVOKED"));

        authenticationFilter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Active session and valid token populates exchange attributes and passes through")
    void filter_validTokenAndSession_passesThrough() {
        String token = "valid-token-active-session";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/finance/ledger")
                .header(HeaderConstants.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getSessionId(token)).thenReturn("active-sess-123");
        when(jwtTokenProvider.getUserId(token)).thenReturn("user-1");
        when(jwtTokenProvider.getCommunityId(token)).thenReturn("comm-1");
        when(jwtTokenProvider.getRoles(token)).thenReturn(List.of("RESIDENT"));
        when(cacheManager.getCachedValue("revoked_sessions", "active-sess-123")).thenReturn(Mono.empty());

        boolean[] chainCalled = new boolean[]{false};
        authenticationFilter.filter(exchange, ex -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
        assertThat((String) exchange.getAttribute(GatewayConstants.COMMUNITY_ID_ATTR)).isEqualTo("comm-1");
        assertThat((String) exchange.getAttribute(GatewayConstants.USER_ID_ATTR)).isEqualTo("user-1");
    }
}
