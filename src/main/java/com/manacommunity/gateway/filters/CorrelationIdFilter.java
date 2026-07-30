package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * First filter in the chain. Establishes correlation and request IDs for
 * distributed tracing, stores the request start time, and sets MDC context
 * so all downstream log statements include these identifiers.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -100;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
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

        // Mutate request to carry headers downstream
        final String finalCorrelationId = correlationId;
        final String finalRequestId = requestId;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HeaderConstants.X_CORRELATION_ID, finalCorrelationId)
                .header(HeaderConstants.X_REQUEST_ID, finalRequestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        // Add correlation and request IDs to the response headers
        mutatedExchange.getResponse().getHeaders().set(HeaderConstants.X_CORRELATION_ID, finalCorrelationId);
        mutatedExchange.getResponse().getHeaders().set(HeaderConstants.X_REQUEST_ID, finalRequestId);

        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    MDC.remove("correlationId");
                    MDC.remove("requestId");
                });
    }
}
