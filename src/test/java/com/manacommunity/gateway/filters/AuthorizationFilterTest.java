package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.authorization.PermissionEvaluator;
import com.manacommunity.gateway.authorization.PolicyAuthorization;
import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.security.GatewaySecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private PermissionEvaluator permissionEvaluator;
    private PolicyAuthorization policyAuthorization;
    private GatewaySecurityProperties securityProperties;
    private AuthorizationFilter authorizationFilter;

    @BeforeEach
    void setUp() {
        permissionEvaluator = new PermissionEvaluator();
        policyAuthorization = new PolicyAuthorization(permissionEvaluator);
        securityProperties = new GatewaySecurityProperties();
        securityProperties.setRoleMappings(Map.of(
                "/api/v*/governance/admin/**", List.of("ADMIN", "SUPER_ADMIN")
        ));

        authorizationFilter = new AuthorizationFilter(permissionEvaluator, policyAuthorization, securityProperties);
    }

    @Test
    void filter_whenPublicPath_shouldBypass() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(authorizationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_whenUnauthenticated_shouldProceedWithoutPolicyEvaluation() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resident/list").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(authorizationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_whenNoSpecificRoleRequired_shouldAllowAuthenticatedUser() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resident/list").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayConstants.USER_ID_ATTR, "user-123");
        exchange.getAttributes().put(GatewayConstants.ROLES_ATTR, List.of("RESIDENT"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(authorizationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_whenPathRoleMatches_shouldAllowAccess() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/governance/admin/audit-logs").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayConstants.USER_ID_ATTR, "user-admin");
        exchange.getAttributes().put(GatewayConstants.ROLES_ATTR, List.of("ADMIN"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(authorizationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_whenPathRoleMismatch_shouldReturn403Forbidden() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/governance/admin/audit-logs").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayConstants.USER_ID_ATTR, "user-regular");
        exchange.getAttributes().put(GatewayConstants.ROLES_ATTR, List.of("RESIDENT"));

        StepVerifier.create(authorizationFilter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(chain);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void filter_whenRouteMetadataRequiresRole_shouldEnforce() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/custom-route/action").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayConstants.USER_ID_ATTR, "user-custom");
        exchange.getAttributes().put(GatewayConstants.ROLES_ATTR, List.of("SUPER_ADMIN"));

        Route route = Route.async()
                .id("custom-route")
                .uri(URI.create("http://localhost:8080"))
                .order(0)
                .predicate(swe -> true)
                .metadata("roles", List.of("SUPER_ADMIN"))
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(authorizationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }
}
