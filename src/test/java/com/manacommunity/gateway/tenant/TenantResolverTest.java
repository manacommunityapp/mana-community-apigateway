package com.manacommunity.gateway.tenant;

import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantResolverTest {

    private TenantResolver tenantResolver;

    @BeforeEach
    void setUp() {
        tenantResolver = new TenantResolver();
        ReflectionTestUtils.setField(tenantResolver, "defaultTenant", "default");
    }

    @Test
    void resolveTenantId_fromAttribute() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resident/list").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayConstants.TENANT_ID_ATTR, "tenant-from-attr");

        String tenantId = tenantResolver.resolveTenantId(exchange);
        assertEquals("tenant-from-attr", tenantId);
    }

    @Test
    void resolveTenantId_fromHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resident/list")
                .header(HeaderConstants.X_TENANT_ID, "tenant-from-header")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String tenantId = tenantResolver.resolveTenantId(exchange);
        assertEquals("tenant-from-header", tenantId);
    }

    @Test
    void resolveTenantId_fallsBackToDefault() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/resident/list").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String tenantId = tenantResolver.resolveTenantId(exchange);
        assertEquals("default", tenantId);
    }
}
