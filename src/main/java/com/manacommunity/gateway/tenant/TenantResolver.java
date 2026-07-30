package com.manacommunity.gateway.tenant;

import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves tenant identity from exchange attributes (set by AuthenticationFilter from JWT)
 * or from request headers as a fallback.
 */
@Slf4j
@Component
public class TenantResolver {

    @Value("${gateway.tenant.default-tenant:default}")
    private String defaultTenant;

    /**
     * Resolves the tenant ID from exchange attributes or request headers.
     * Falls back to the configured default tenant if none is found.
     */
    public String resolveTenantId(ServerWebExchange exchange) {
        // First check exchange attributes (populated by AuthenticationFilter from JWT)
        String tenantId = exchange.getAttribute(GatewayConstants.TENANT_ID_ATTR);
        if (tenantId != null && !tenantId.isBlank()) {
            log.debug("Resolved tenantId from exchange attributes: {}", tenantId);
            return tenantId;
        }

        // Fallback to request header
        tenantId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_TENANT_ID);
        if (tenantId != null && !tenantId.isBlank()) {
            log.debug("Resolved tenantId from request header: {}", tenantId);
            return tenantId;
        }

        log.debug("No tenantId found, using default: {}", defaultTenant);
        return defaultTenant;
    }

    /**
     * Resolves the tenant code from exchange attributes or request headers.
     */
    public String resolveTenantCode(ServerWebExchange exchange) {
        String tenantCode = exchange.getAttribute(GatewayConstants.TENANT_CODE_ATTR);
        if (tenantCode != null && !tenantCode.isBlank()) {
            return tenantCode;
        }

        tenantCode = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_TENANT_CODE);
        if (tenantCode != null && !tenantCode.isBlank()) {
            return tenantCode;
        }

        return null;
    }

    /**
     * Resolves the community ID from exchange attributes or request headers.
     */
    public String resolveCommunityId(ServerWebExchange exchange) {
        String communityId = exchange.getAttribute(GatewayConstants.COMMUNITY_ID_ATTR);
        if (communityId != null && !communityId.isBlank()) {
            return communityId;
        }

        communityId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_COMMUNITY_ID);
        if (communityId != null && !communityId.isBlank()) {
            return communityId;
        }

        return null;
    }
}
