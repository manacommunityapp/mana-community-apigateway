package com.manacommunity.gateway.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manacommunity.gateway.authorization.PermissionEvaluator;
import com.manacommunity.gateway.authorization.PolicyAuthorization;
import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Collections;
import java.util.List;

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

    @Value("${gateway.security.public-paths}")
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

        // Evaluate authorization policy
        // Route-specific roles/permissions can be extended via configuration;
        // the default policy allows any authenticated user to proceed.
        List<String> requiredRoles = Collections.emptyList();
        List<String> requiredPermissions = Collections.emptyList();

        boolean authorized = policyAuthorization.evaluate(exchange, requiredRoles, requiredPermissions);

        if (!authorized) {
            log.warn("Access denied for user [{}] on path [{}]", userId, requestPath);
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                    "You do not have permission to access this resource");
        }

        return chain.filter(exchange);
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
