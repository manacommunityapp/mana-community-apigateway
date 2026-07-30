package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Logs response metadata after the filter chain completes, including
 * status code, execution time, and tracing identifiers.
 */
@Slf4j
@Component
public class ResponseLoggingFilter implements GlobalFilter, Ordered {

    private static final int ORDER = 100;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    Instant startTime = exchange.getAttribute(GatewayConstants.REQUEST_START_TIME_ATTR);
                    long durationMs = 0;
                    if (startTime != null) {
                        durationMs = Duration.between(startTime, Instant.now()).toMillis();
                    }

                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode != null ? statusCode.value() : 0;

                    String correlationId = exchange.getAttribute(GatewayConstants.CORRELATION_ID_ATTR);
                    String requestId = exchange.getAttribute(GatewayConstants.REQUEST_ID_ATTR);

                    log.info("Response: status={}, durationMs={}, correlationId={}, requestId={}, path={}",
                            status,
                            durationMs,
                            correlationId,
                            requestId,
                            exchange.getRequest().getURI().getPath());
                });
    }
}
