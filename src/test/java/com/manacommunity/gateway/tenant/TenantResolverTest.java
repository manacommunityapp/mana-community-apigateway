package com.manacommunity.gateway.tenant;

import com.manacommunity.gateway.constants.HeaderConstants;
import com.manacommunity.gateway.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantResolverTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private TenantResolver tenantResolver;
    private TenantProperties tenantProperties;

    @BeforeEach
    void setUp() {
        tenantProperties = new TenantProperties();
        tenantProperties.setHeaderName("X-Tenant-Id");
        tenantProperties.setDefaultTenant("default");
        tenantProperties.setSupportedTypes(List.of("APARTMENT", "VILLA"));
        tenantResolver = new TenantResolver(tenantProperties, jwtTokenProvider);
    }

    @Test
    void resolveTenantId_fromHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resident/list")
                .header(HeaderConstants.X_TENANT_ID, "tenant-from-header")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(tenantResolver.resolveTenantId(exchange))
                .expectNext("tenant-from-header")
                .verifyComplete();
    }

    @Test
    void resolveTenantId_fromJwt() {
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getTenantId(anyString())).thenReturn("tenant-from-jwt");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resident/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(tenantResolver.resolveTenantId(exchange))
                .expectNext("tenant-from-jwt")
                .verifyComplete();
    }

    @Test
    void resolveTenantId_fallsBackToDefault() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resident/list").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(tenantResolver.resolveTenantId(exchange))
                .expectNext("default")
                .verifyComplete();
    }

    @Test
    void resolveTenantId_fromSubdomain() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resident/list")
                .header(HttpHeaders.HOST, "myapartment.api.manacommunity.com")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(tenantResolver.resolveTenantId(exchange))
                .expectNext("myapartment")
                .verifyComplete();
    }
}
