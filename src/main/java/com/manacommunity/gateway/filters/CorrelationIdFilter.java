package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Top-level WebFilter establishing correlation and request IDs for distributed tracing,
 * generating W3C traceparent headers, storing the request start time, and setting MDC
 * context so all application and downstream log statements include these identifiers.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements WebFilter, Ordered {

    private static final int ORDER = -100;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Reuse existing correlation ID from the request header, or generate a new one
        String correlationId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Always generate a unique request ID for this specific request
        String requestId = UUID.randomUUID().toString();

        // Store in exchange attributes for downstream filters
        exchange.getAttributes().put(GatewayConstants.CORRELATION_ID_ATTR, correlationId);
        exchange.getAttributes().put(GatewayConstants.REQUEST_ID_ATTR, requestId);
        exchange.getAttributes().put(GatewayConstants.REQUEST_START_TIME_ATTR, Instant.now());

        // Set MDC for structured logging
        MDC.put("correlationId", correlationId);
        MDC.put("requestId", requestId);

        log.debug("Assigned correlationId [{}] and requestId [{}]", correlationId, requestId);

        final String finalCorrelationId = correlationId;
        final String finalRequestId = requestId;

        // Establish W3C traceparent header for distributed tracing
        String traceparent = exchange.getRequest().getHeaders().getFirst(HeaderConstants.TRACEPARENT);
        if (traceparent == null || traceparent.isBlank()) {
            String traceId = finalCorrelationId.replace("-", "");
            if (traceId.length() < 32) {
                traceId = String.format("%-32s", traceId).replace(' ', '0');
            } else if (traceId.length() > 32) {
                traceId = traceId.substring(0, 32);
            }
            String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            traceparent = "00-" + traceId + "-" + spanId + "-01";
        }
        final String finalTraceparent = traceparent;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HeaderConstants.X_CORRELATION_ID, finalCorrelationId)
                .header(HeaderConstants.X_REQUEST_ID, finalRequestId)
                .header(HeaderConstants.TRACEPARENT, finalTraceparent)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        // Add correlation, request IDs, and traceparent to response headers
        mutatedExchange.getResponse().getHeaders().set(HeaderConstants.X_CORRELATION_ID, finalCorrelationId);
        mutatedExchange.getResponse().getHeaders().set(HeaderConstants.X_REQUEST_ID, finalRequestId);
        mutatedExchange.getResponse().getHeaders().set(HeaderConstants.TRACEPARENT, finalTraceparent);

        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    MDC.remove("correlationId");
                    MDC.remove("requestId");
                });
    }
}