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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manacommunity.gateway.dto.ErrorResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    private final TenantResolver tenantResolver;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String jwtCommunityId = exchange.getAttribute(GatewayConstants.COMMUNITY_ID_ATTR);
        String headerCommunityId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_COMMUNITY_ID);
        List<String> roles = exchange.getAttribute(GatewayConstants.ROLES_ATTR);
        boolean isSuperAdmin = roles != null && roles.stream().anyMatch(r -> r.equalsIgnoreCase("SUPER_ADMIN") || r.equalsIgnoreCase("ROLE_SUPER_ADMIN"));

        // Cross-community validation: if a JWT is bound to a community and client attempts to spoof a different X-Community-Id
        if (jwtCommunityId != null && !jwtCommunityId.isBlank()
                && headerCommunityId != null && !headerCommunityId.isBlank()
                && !isSuperAdmin
                && !jwtCommunityId.trim().equalsIgnoreCase(headerCommunityId.trim())) {
            log.warn("Cross-community access violation: token community [{}] does not match requested header [{}] for user [{}]",
                    jwtCommunityId, headerCommunityId, exchange.getAttribute(GatewayConstants.USER_ID_ATTR));
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "Cross-community access denied: token not authorized for requested community");
        }

        String organizationId = tenantResolver.resolveOrganizationId(exchange);
        String tenantId = tenantResolver.resolveTenantId(exchange);
        String tenantCode = tenantResolver.resolveTenantCode(exchange);
        String communityId = tenantResolver.resolveCommunityId(exchange);

        // Store in exchange attributes
        TenantContext.setOrganizationId(exchange, organizationId);
        TenantContext.setTenantId(exchange, tenantId);
        TenantContext.setTenantCode(exchange, tenantCode);
        TenantContext.setCommunityId(exchange, communityId);

        // Set MDC for logging
        if (organizationId != null) {
            MDC.put("organizationId", organizationId);
        }
        if (tenantId != null) {
            MDC.put("tenantId", tenantId);
        }
        if (tenantCode != null) {
            MDC.put("tenantCode", tenantCode);
        }

        log.debug("Resolved tenant: orgId={}, id={}, code={}, communityId={}", organizationId, tenantId, tenantCode, communityId);

        // Mutate request to add tenant headers for downstream services
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

        if (organizationId != null) {
            requestBuilder.header(HeaderConstants.X_ORGANIZATION_ID, organizationId);
        }
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

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String requestId = exchange.getAttribute(GatewayConstants.REQUEST_ID_ATTR);
        String path = exchange.getRequest().getURI().getPath();

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                requestId
        );

        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            byte[] fallback = ("{\"error\":\"" + status.getReasonPhrase() + "\"}").getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(fallback);
            return response.writeWith(Mono.just(buffer));
        }
    }
}
