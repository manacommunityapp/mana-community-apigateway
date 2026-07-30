package com.manacommunity.gateway.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;

import java.util.List;

public final class RequestUtil {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private RequestUtil() {
        // Utility class - prevent instantiation
    }

    public static String extractBearerToken(ServerHttpRequest request) {
        if (request == null) {
            return null;
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }

        return null;
    }

    public static boolean isPublicPath(String path, List<String> publicPaths) {
        if (path == null || publicPaths == null || publicPaths.isEmpty()) {
            return false;
        }

        return publicPaths.stream()
                .anyMatch(publicPath -> PATH_MATCHER.match(publicPath, path));
    }

    public static String getDeviceInfo(ServerHttpRequest request) {
        if (request == null) {
            return null;
        }

        return request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
    }
}
