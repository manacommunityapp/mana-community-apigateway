package com.manacommunity.gateway.tenant;

import com.manacommunity.gateway.constants.GatewayConstants;
import org.springframework.web.server.ServerWebExchange;

/**
 * Utility class for storing and retrieving tenant information
 * via ServerWebExchange attributes in a reactive context.
 */
public final class TenantContext {

    private TenantContext() {
        // Utility class — prevent instantiation
    }

    public static void setTenantId(ServerWebExchange exchange, String tenantId) {
        if (exchange != null && tenantId != null) {
            exchange.getAttributes().put(GatewayConstants.TENANT_ID_ATTR, tenantId);
        }
    }

    public static String getTenantId(ServerWebExchange exchange) {
        if (exchange == null) {
            return null;
        }
        return exchange.getAttribute(GatewayConstants.TENANT_ID_ATTR);
    }

    public static void setTenantCode(ServerWebExchange exchange, String tenantCode) {
        if (exchange != null && tenantCode != null) {
            exchange.getAttributes().put(GatewayConstants.TENANT_CODE_ATTR, tenantCode);
        }
    }

    public static String getTenantCode(ServerWebExchange exchange) {
        if (exchange == null) {
            return null;
        }
        return exchange.getAttribute(GatewayConstants.TENANT_CODE_ATTR);
    }

    public static void setCommunityId(ServerWebExchange exchange, String communityId) {
        if (exchange != null && communityId != null) {
            exchange.getAttributes().put(GatewayConstants.COMMUNITY_ID_ATTR, communityId);
        }
    }

    public static String getCommunityId(ServerWebExchange exchange) {
        if (exchange == null) {
            return null;
        }
        return exchange.getAttribute(GatewayConstants.COMMUNITY_ID_ATTR);
    }
}
