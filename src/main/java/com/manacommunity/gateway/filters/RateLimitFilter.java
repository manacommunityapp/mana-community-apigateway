package com.manacommunity.gateway.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import com.manacommunity.gateway.dto.ErrorResponse;
import com.manacommunity.gateway.ratelimit.RateLimitConfig;
import com.manacommunity.gateway.ratelimit.RedisRateLimiter;
import com.manacommunity.gateway.security.GatewaySecurityProperties;
import com.manacommunity.gateway.tenant.TenantResolver;
import com.manacommunity.gateway.util.IpAddressUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Reactive rate limiting filter. Evaluates rate limit policies per tenant, client IP,
 * and endpoint pattern using Redis sliding-window counters.
 * Executes at order -35 (after TenantFilter at -40 and before AuthorizationFilter at -30).
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -35;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> EXCLUDED_INFRA_PATHS = List.of(
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/fallback/**",
            "/gateway/**"
    );

    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitConfig rateLimitConfig;
    private final TenantResolver tenantResolver;
    private final GatewaySecurityProperties securityProperties;

    public RateLimitFilter(
            @org.springframework.beans.factory.annotation.Qualifier("customRedisRateLimiter") RedisRateLimiter redisRateLimiter,
            RateLimitConfig rateLimitConfig,
            TenantResolver tenantResolver,
            GatewaySecurityProperties securityProperties) {
        this.redisRateLimiter = redisRateLimiter;
        this.rateLimitConfig = rateLimitConfig;
        this.tenantResolver = tenantResolver;
        this.securityProperties = securityProperties;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!rateLimitConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();

        // Skip rate limiting for static infrastructure/health/doc endpoints
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        String resolvedTenant = tenantResolver.resolveTenantId(exchange);
        final String tenantId = (resolvedTenant == null || resolvedTenant.isBlank()) ? "default" : resolvedTenant;

        String clientIp = IpAddressUtil.getClientIp(exchange.getRequest());
        int[] limits = rateLimitConfig.parseEndpointConfig(path);
        int maxRequests = limits[0];
        int durationSeconds = limits[1];

        String key = RedisRateLimiter.formatKey(tenantId, clientIp, path);

        return redisRateLimiter.isAllowed(key, maxRequests, durationSeconds)
                .flatMap(allowed -> {
                    if (Boolean.TRUE.equals(allowed)) {
                        exchange.getResponse().getHeaders().set(HeaderConstants.X_RATE_LIMIT_LIMIT, String.valueOf(maxRequests));
                        return chain.filter(exchange);
                    }

                    log.warn("Rate limit exceeded for tenant [{}], client IP [{}], path [{}]. Limit: {}/{}s",
                            tenantId, clientIp, path, maxRequests, durationSeconds);
                    return writeRateLimitExceededResponse(exchange, maxRequests, durationSeconds, path);
                });
    }

    private boolean isExcludedPath(String requestPath) {
        if (requestPath == null) {
            return false;
        }
        return EXCLUDED_INFRA_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
    }

    private Mono<Void> writeRateLimitExceededResponse(ServerWebExchange exchange, int maxRequests, int durationSeconds, String path) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        long resetEpoch = Instant.now().plusSeconds(durationSeconds).getEpochSecond();
        response.getHeaders().set(HeaderConstants.X_RATE_LIMIT_LIMIT, String.valueOf(maxRequests));
        response.getHeaders().set(HeaderConstants.X_RATE_LIMIT_REMAINING, "0");
        response.getHeaders().set(HeaderConstants.X_RATE_LIMIT_RESET, String.valueOf(resetEpoch));

        String requestId = exchange.getAttribute(GatewayConstants.REQUEST_ID_ATTR);

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded. Please try again later.",
                path,
                requestId
        );

        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize rate limit error response", e);
            byte[] fallback = ("{\"error\":\"" + HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase() + "\"}").getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(fallback);
            return response.writeWith(Mono.just(buffer));
        }
    }
}
