package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import com.manacommunity.gateway.tenant.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantFilterTest {

    private TenantResolver tenantResolver;
    private TenantFilter tenantFilter;

    @BeforeEach
    void setUp() {
        tenantResolver = mock(TenantResolver.class);
        when(tenantResolver.resolveOrganizationId(any())).thenReturn("org-1");
        when(tenantResolver.resolveTenantId(any())).thenReturn("comm-100");
        when(tenantResolver.resolveTenantCode(any())).thenReturn("CODE-100");
        when(tenantResolver.resolveCommunityId(any())).thenReturn("comm-100");

        tenantFilter = new TenantFilter(tenantResolver);
    }

    @Test
    @DisplayName("Cross-community access is denied with 403 when token community does not match requested header")
    void filter_crossCommunityAccess_returnsForbidden() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/finance/ledger")
                .header(HeaderConstants.X_COMMUNITY_ID, "comm-200")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayConstants.COMMUNITY_ID_ATTR, "comm-100");
        exchange.getAttributes().put(GatewayConstants.USER_ID_ATTR, "user-cross");
        exchange.getAttributes().put(GatewayConstants.ROLES_ATTR, List.of("RESIDENT"));

        tenantFilter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Super admin is allowed cross-community access")
    void filter_superAdmin_allowsCrossCommunityAccess() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/finance/ledger")
                .header(HeaderConstants.X_COMMUNITY_ID, "comm-200")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayConstants.COMMUNITY_ID_ATTR, "comm-100");
        exchange.getAttributes().put(GatewayConstants.USER_ID_ATTR, "super-admin");
        exchange.getAttributes().put(GatewayConstants.ROLES_ATTR, List.of("SUPER_ADMIN"));

        boolean[] chainCalled = new boolean[]{false};
        tenantFilter.filter(exchange, ex -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("Matching community allows access through chain")
    void filter_matchingCommunity_allowsAccess() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/finance/ledger")
                .header(HeaderConstants.X_COMMUNITY_ID, "comm-100")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayConstants.COMMUNITY_ID_ATTR, "comm-100");
        exchange.getAttributes().put(GatewayConstants.USER_ID_ATTR, "user-normal");
        exchange.getAttributes().put(GatewayConstants.ROLES_ATTR, List.of("RESIDENT"));

        boolean[] chainCalled = new boolean[]{false};
        tenantFilter.filter(exchange, ex -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }
}
