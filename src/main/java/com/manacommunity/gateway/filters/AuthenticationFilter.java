package com.manacommunity.gateway.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import com.manacommunity.gateway.dto.ErrorResponse;
import com.manacommunity.gateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
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
 * Validates JWT tokens on protected endpoints and populates exchange attributes
 * with authenticated user claims for consumption by downstream filters.
 * Public paths (configured via gateway.security.public-paths) bypass authentication.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -50;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${gateway.security.public-paths}")
    private List<String> publicPaths;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestPath = exchange.getRequest().getURI().getPath();

        // Skip authentication for public paths
        if (isPublicPath(requestPath)) {
            log.debug("Public path accessed: {}", requestPath);
            return chain.filter(exchange);
        }

        // Extract the Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HeaderConstants.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(HeaderConstants.BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", requestPath);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(HeaderConstants.BEARER_PREFIX.length());

        // Validate the token
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Invalid or expired JWT token for path: {}", requestPath);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    "Invalid or expired authentication token");
        }

        // Extract claims and populate exchange attributes
        try {
            populateExchangeAttributes(exchange, token);
        } catch (Exception e) {
            log.error("Failed to extract claims from JWT token: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    "Failed to process authentication token");
        }

        return chain.filter(exchange);
    }

    private void populateExchangeAttributes(ServerWebExchange exchange, String token) {
        String userId = jwtTokenProvider.getUserId(token);
        String username = jwtTokenProvider.getUsername(token);
        String tenantId = jwtTokenProvider.getTenantId(token);
        String tenantCode = jwtTokenProvider.getTenantCode(token);
        String communityId = jwtTokenProvider.getCommunityId(token);
        List<String> roles = jwtTokenProvider.getRoles(token);
        List<String> permissions = jwtTokenProvider.getPermissions(token);
        String email = jwtTokenProvider.getEmail(token);
        String mobile = jwtTokenProvider.getMobile(token);
        String deviceId = jwtTokenProvider.getDeviceId(token);
        String sessionId = jwtTokenProvider.getSessionId(token);

        setAttributeIfPresent(exchange, GatewayConstants.USER_ID_ATTR, userId);
        setAttributeIfPresent(exchange, GatewayConstants.USERNAME_ATTR, username);
        setAttributeIfPresent(exchange, GatewayConstants.TENANT_ID_ATTR, tenantId);
        setAttributeIfPresent(exchange, GatewayConstants.TENANT_CODE_ATTR, tenantCode);
        setAttributeIfPresent(exchange, GatewayConstants.COMMUNITY_ID_ATTR, communityId);
        setAttributeIfPresent(exchange, GatewayConstants.EMAIL_ATTR, email);
        setAttributeIfPresent(exchange, GatewayConstants.MOBILE_ATTR, mobile);
        setAttributeIfPresent(exchange, GatewayConstants.DEVICE_ID_ATTR, deviceId);
        setAttributeIfPresent(exchange, GatewayConstants.SESSION_ID_ATTR, sessionId);

        if (roles != null) {
            exchange.getAttributes().put(GatewayConstants.ROLES_ATTR, roles);
        }
        if (permissions != null) {
            exchange.getAttributes().put(GatewayConstants.PERMISSIONS_ATTR, permissions);
        }

        // Set MDC for downstream logging
        if (userId != null) {
            MDC.put("userId", userId);
        }
        if (tenantId != null) {
            MDC.put("tenantId", tenantId);
        }

        log.debug("Authenticated user [{}] (username: {}) for tenant [{}]", userId, username, tenantId);
    }

    private void setAttributeIfPresent(ServerWebExchange exchange, String key, String value) {
        if (value != null) {
            exchange.getAttributes().put(key, value);
        }
    }

    private boolean isPublicPath(String requestPath) {
        if (publicPaths == null || publicPaths.isEmpty()) {
            return false;
        }
        return publicPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
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
