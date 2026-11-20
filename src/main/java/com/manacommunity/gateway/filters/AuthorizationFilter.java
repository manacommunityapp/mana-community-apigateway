package com.manacommunity.gateway.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manacommunity.gateway.authorization.PermissionEvaluator;
import com.manacommunity.gateway.authorization.PolicyAuthorization;
import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.dto.ErrorResponse;
import com.manacommunity.gateway.security.GatewaySecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Evaluates authorization policies for protected endpoints. Uses
 * {@link PolicyAuthorization} to verify that the authenticated user
 * possesses the required roles and permissions for the requested route.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -30;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    private final PermissionEvaluator permissionEvaluator;
    private final PolicyAuthorization policyAuthorization;
    private final GatewaySecurityProperties securityProperties;

    @Value("${gateway.security.public-paths:/api/v*/auth/login,/api/v*/auth/register,/actuator/health}")
    private List<String> publicPaths;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestPath = exchange.getRequest().getURI().getPath();

        // Skip authorization for public paths
        if (isPublicPath(requestPath)) {
            return chain.filter(exchange);
        }

        // Check if user has been authenticated (attributes set by AuthenticationFilter)
        String userId = exchange.getAttribute(GatewayConstants.USER_ID_ATTR);
        if (userId == null) {
            log.warn("Authorization check skipped: no authenticated user for path [{}]", requestPath);
            return chain.filter(exchange);
        }

        // Dynamically evaluate authorization policy based on route metadata or path mappings
        List<String> requiredRoles = resolveRequiredRoles(exchange, requestPath);
        List<String> requiredPermissions = resolveRequiredPermissions(exchange, requestPath);

        boolean authorized = policyAuthorization.evaluate(exchange, requiredRoles, requiredPermissions);

        if (!authorized) {
            log.warn("Access denied for user [{}] on path [{}]. Required roles: {}, required permissions: {}",
                    userId, requestPath, requiredRoles, requiredPermissions);
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                    "You do not have permission to access this resource");
        }

        return chain.filter(exchange);
    }

    private List<String> resolveRequiredRoles(ServerWebExchange exchange, String requestPath) {
        // 1. Check Route metadata if available
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null && route.getMetadata() != null) {
            Object rolesMeta = route.getMetadata().get("roles");
            if (rolesMeta instanceof Collection<?> col) {
                return col.stream().map(Object::toString).toList();
            } else if (rolesMeta instanceof String str && !str.isBlank()) {
                return List.of(str.split(","));
            }
        }

        // 2. Check path-based role mappings configured in securityProperties
        if (securityProperties != null && securityProperties.getRoleMappings() != null) {
            for (Map.Entry<String, List<String>> entry : securityProperties.getRoleMappings().entrySet()) {
                if (PATH_MATCHER.match(entry.getKey(), requestPath)) {
                    return entry.getValue();
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> resolveRequiredPermissions(ServerWebExchange exchange, String requestPath) {
        // 1. Check Route metadata if available
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null && route.getMetadata() != null) {
            Object permMeta = route.getMetadata().get("permissions");
            if (permMeta instanceof Collection<?> col) {
                return col.stream().map(Object::toString).toList();
            } else if (permMeta instanceof String str && !str.isBlank()) {
                return List.of(str.split(","));
            }
        }

        // 2. Check path-based permission mappings configured in securityProperties
        if (securityProperties != null && securityProperties.getPermissionMappings() != null) {
            for (Map.Entry<String, List<String>> entry : securityProperties.getPermissionMappings().entrySet()) {
                if (PATH_MATCHER.match(entry.getKey(), requestPath)) {
                    return entry.getValue();
                }
            }
        }

        return Collections.emptyList();
    }

    private boolean isPublicPath(String requestPath) {
        if (securityProperties != null && securityProperties.getPublicPaths() != null && !securityProperties.getPublicPaths().isEmpty()) {
            if (securityProperties.getPublicPaths().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath))) {
                return true;
            }
        }
        if (publicPaths != null && !publicPaths.isEmpty()) {
            return publicPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
        }
        return false;
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
