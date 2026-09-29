package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds standard tracing and security response headers before the response
 * is committed to the client. Uses {@code beforeCommit} to guarantee that
 * headers are applied regardless of how the response is written.
 */
@Slf4j
@Component
public class ResponseTransformationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = 90;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // Tracing headers
            String correlationId = exchange.getAttribute(GatewayConstants.CORRELATION_ID_ATTR);
            String requestId = exchange.getAttribute(GatewayConstants.REQUEST_ID_ATTR);

            if (correlationId != null) {
                headers.set(HeaderConstants.X_CORRELATION_ID, correlationId);
            }
            if (requestId != null) {
                headers.set(HeaderConstants.X_REQUEST_ID, requestId);
            }

            // Security headers (OWASP ASVS 14.4.3 compliant)
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("X-XSS-Protection", "0");
            headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
            headers.set("Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: https: blob:; connect-src 'self' https: wss: ws:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self';");
            headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
            headers.set("Permissions-Policy",
                    "camera=(), microphone=(), geolocation=(), payment=()");

            // Disable caching for non-GET requests
            HttpMethod method = exchange.getRequest().getMethod();
            if (method != null && method != HttpMethod.GET) {
                headers.set("Cache-Control", "no-store");
            }

            return Mono.empty();
        });

        return chain.filter(exchange);
    }
}
