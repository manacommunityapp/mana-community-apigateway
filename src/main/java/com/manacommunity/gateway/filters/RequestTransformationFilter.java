package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

/**
 * Transforms the outbound request by adding downstream headers derived from
 * exchange attributes populated by earlier filters (authentication, tenant, etc.).
 * Removes the original Authorization header to enforce zero-trust between services.
 */
@Slf4j
@Component
public class RequestTransformationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -10;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

        // User identity headers
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_USER_ID,
                exchange.getAttribute(GatewayConstants.USER_ID_ATTR));
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_USERNAME,
                exchange.getAttribute(GatewayConstants.USERNAME_ATTR));
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_USER_EMAIL,
                exchange.getAttribute(GatewayConstants.EMAIL_ATTR));
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_USER_MOBILE,
                exchange.getAttribute(GatewayConstants.MOBILE_ATTR));

        // User roles and permissions (comma-separated)
        List<String> roles = exchange.getAttribute(GatewayConstants.ROLES_ATTR);
        if (roles != null && !roles.isEmpty()) {
            requestBuilder.header(HeaderConstants.X_USER_ROLES, String.join(",", roles));
        }

        List<String> permissions = exchange.getAttribute(GatewayConstants.PERMISSIONS_ATTR);
        if (permissions != null && !permissions.isEmpty()) {
            requestBuilder.header(HeaderConstants.X_USER_PERMISSIONS, String.join(",", permissions));
        }

        // Device and session headers
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_DEVICE_ID,
                exchange.getAttribute(GatewayConstants.DEVICE_ID_ATTR));
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_SESSION_ID,
                exchange.getAttribute(GatewayConstants.SESSION_ID_ATTR));

        // Correlation and request tracing headers
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_CORRELATION_ID,
                exchange.getAttribute(GatewayConstants.CORRELATION_ID_ATTR));
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_REQUEST_ID,
                exchange.getAttribute(GatewayConstants.REQUEST_ID_ATTR));

        // Tenant headers
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_TENANT_ID,
                exchange.getAttribute(GatewayConstants.TENANT_ID_ATTR));
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_TENANT_CODE,
                exchange.getAttribute(GatewayConstants.TENANT_CODE_ATTR));
        addHeaderIfPresent(requestBuilder, HeaderConstants.X_COMMUNITY_ID,
                exchange.getAttribute(GatewayConstants.COMMUNITY_ID_ATTR));

        // Client IP forwarding
        String clientIp = resolveClientIp(exchange);
        if (clientIp != null) {
            requestBuilder.header(HeaderConstants.X_FORWARDED_FOR, clientIp);
            requestBuilder.header(HeaderConstants.X_REAL_IP, clientIp);
        }

        // Remove the Authorization header (zero-trust: downstream validates via internal headers)
        requestBuilder.headers(headers -> headers.remove(HeaderConstants.AUTHORIZATION));

        ServerHttpRequest transformedRequest = requestBuilder.build();
        ServerWebExchange transformedExchange = exchange.mutate().request(transformedRequest).build();

        log.debug("Request transformed for downstream: path={}, userId={}, tenantId={}",
                exchange.getRequest().getURI().getPath(),
                exchange.getAttribute(GatewayConstants.USER_ID_ATTR),
                exchange.getAttribute(GatewayConstants.TENANT_ID_ATTR));

        return chain.filter(transformedExchange);
    }

    private void addHeaderIfPresent(ServerHttpRequest.Builder builder, String headerName, Object value) {
        if (value != null) {
            builder.header(headerName, value.toString());
        }
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        String xForwardedFor = request.getHeaders().getFirst(HeaderConstants.X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress)
                .orElse(null);
    }
}
