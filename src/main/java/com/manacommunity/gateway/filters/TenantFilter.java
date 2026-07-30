package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import com.manacommunity.gateway.tenant.TenantContext;
import com.manacommunity.gateway.tenant.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Resolves the current tenant from JWT claims or request headers,
 * stores it in the exchange attributes and TenantContext, and
 * propagates tenant headers to downstream services.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -40;

    private final TenantResolver tenantResolver;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tenantId = tenantResolver.resolveTenantId(exchange);
        String tenantCode = tenantResolver.resolveTenantCode(exchange);
        String communityId = tenantResolver.resolveCommunityId(exchange);

        // Store in exchange attributes
        TenantContext.setTenantId(exchange, tenantId);
        TenantContext.setTenantCode(exchange, tenantCode);
        TenantContext.setCommunityId(exchange, communityId);

        // Set MDC for logging
        if (tenantId != null) {
            MDC.put("tenantId", tenantId);
        }
        if (tenantCode != null) {
            MDC.put("tenantCode", tenantCode);
        }

        log.debug("Resolved tenant: id={}, code={}, communityId={}", tenantId, tenantCode, communityId);

        // Mutate request to add tenant headers for downstream services
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

        if (tenantId != null) {
            requestBuilder.header(HeaderConstants.X_TENANT_ID, tenantId);
        }
        if (tenantCode != null) {
            requestBuilder.header(HeaderConstants.X_TENANT_CODE, tenantCode);
        }
        if (communityId != null) {
            requestBuilder.header(HeaderConstants.X_COMMUNITY_ID, communityId);
        }

        ServerHttpRequest mutatedRequest = requestBuilder.build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return chain.filter(mutatedExchange);
    }
}
