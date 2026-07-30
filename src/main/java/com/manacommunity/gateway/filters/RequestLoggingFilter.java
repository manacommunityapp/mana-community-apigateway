package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Logs incoming request metadata using structured SLF4J logging.
 * Populates MDC with correlation, request, tenant, and user identifiers.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -90;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String correlationId = exchange.getAttribute(GatewayConstants.CORRELATION_ID_ATTR);
        String requestId = exchange.getAttribute(GatewayConstants.REQUEST_ID_ATTR);
        String tenantId = exchange.getAttribute(GatewayConstants.TENANT_ID_ATTR);
        String userId = exchange.getAttribute(GatewayConstants.USER_ID_ATTR);
        String clientIp = resolveClientIp(exchange);
        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);

        // Set MDC context
        setMdcIfPresent("correlationId", correlationId);
        setMdcIfPresent("requestId", requestId);
        setMdcIfPresent("tenantId", tenantId);
        setMdcIfPresent("userId", userId);

        log.info("Incoming request: method={}, uri={}, clientIp={}, correlationId={}, requestId={}, tenantId={}, userAgent={}",
                request.getMethod(),
                request.getURI().getPath(),
                clientIp,
                correlationId,
                requestId,
                tenantId,
                userAgent);

        return chain.filter(exchange);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // Check X-Forwarded-For header first (may be set by upstream proxy)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }

        // Fallback to remote address
        return Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress)
                .orElse("unknown");
    }

    private void setMdcIfPresent(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }
}
