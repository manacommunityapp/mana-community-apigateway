package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Logs an audit trail after the response is committed, capturing the HTTP method,
 * URI, status code, response time, client IP, tenant, user, and correlation identifiers.
 */
@Slf4j
@Component
public class AuditFilter implements GlobalFilter, Ordered {

    private static final int ORDER = 10;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Register a beforeCommit callback to capture response details
        exchange.getResponse().beforeCommit(() -> {
            logAuditTrail(exchange);
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    private void logAuditTrail(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String uri = request.getURI().getPath();

        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode != null ? statusCode.value() : 0;

        // Calculate execution time
        Instant startTime = exchange.getAttribute(GatewayConstants.REQUEST_START_TIME_ATTR);
        long durationMs = 0;
        if (startTime != null) {
            durationMs = Duration.between(startTime, Instant.now()).toMillis();
        }

        String clientIp = resolveClientIp(exchange);
        String tenantId = exchange.getAttribute(GatewayConstants.TENANT_ID_ATTR);
        String userId = exchange.getAttribute(GatewayConstants.USER_ID_ATTR);
        String correlationId = exchange.getAttribute(GatewayConstants.CORRELATION_ID_ATTR);

        log.info("AUDIT: method={}, uri={}, status={}, durationMs={}, clientIp={}, tenantId={}, userId={}, correlationId={}",
                method, uri, status, durationMs, clientIp, tenantId, userId, correlationId);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress)
                .orElse("unknown");
    }
}
