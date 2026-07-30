package com.manacommunity.gateway.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;

public final class IpAddressUtil {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    private IpAddressUtil() {
        // Utility class - prevent instantiation
    }

    public static String getClientIp(ServerHttpRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String xForwardedFor = request.getHeaders().getFirst(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank() && !UNKNOWN.equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For can contain multiple IPs: client, proxy1, proxy2
            // Take the first one (the original client IP)
            String clientIp = xForwardedFor.split(",")[0].trim();
            if (!clientIp.isBlank()) {
                return clientIp;
            }
        }

        String xRealIp = request.getHeaders().getFirst(X_REAL_IP);
        if (xRealIp != null && !xRealIp.isBlank() && !UNKNOWN.equalsIgnoreCase(xRealIp)) {
            return xRealIp.trim();
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return UNKNOWN;
    }
}
