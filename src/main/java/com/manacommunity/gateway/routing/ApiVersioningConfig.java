package com.manacommunity.gateway.routing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuration for API version extraction and validation.
 * <p>
 * Extracts the version number from URL paths matching {@code /api/vN/...},
 * validates it against the supported range (1 to {@link GatewayConstants#MAX_API_VERSION}),
 * and stores it as an exchange attribute for downstream consumption.
 */
@Slf4j
@Configuration
public class ApiVersioningConfig {

    private static final String API_VERSION_ATTR = "apiVersion";

    @Bean
    public ApiVersionFilter apiVersionFilter() {
        return new ApiVersionFilter();
    }

    /**
     * Global filter that extracts and validates the API version from the request path.
     * Runs at order -85, after CorrelationIdFilter but before other processing filters.
     */
    static class ApiVersionFilter implements GlobalFilter, Ordered {

        private static final int ORDER = -85;
        private static final Pattern VERSION_PATTERN = Pattern.compile("^/api/v(\\d+)/.*");
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        static {
            OBJECT_MAPPER.findAndRegisterModules();
        }

        @Override
        public int getOrder() {
            return ORDER;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            String path = exchange.getRequest().getURI().getPath();

            Matcher matcher = VERSION_PATTERN.matcher(path);
            if (!matcher.matches()) {
                // Not a versioned API path, pass through
                return chain.filter(exchange);
            }

            String versionString = matcher.group(1);
            int version;

            try {
                version = Integer.parseInt(versionString);
            } catch (NumberFormatException e) {
                log.warn("Non-numeric API version in path: {}", path);
                return writeErrorResponse(exchange, HttpStatus.BAD_REQUEST,
                        "Invalid API version format in path: " + path);
            }

            if (version < 1 || version > GatewayConstants.MAX_API_VERSION) {
                log.warn("Unsupported API version {} in path: {}. Supported versions: 1 to {}",
                        version, path, GatewayConstants.MAX_API_VERSION);
                return writeErrorResponse(exchange, HttpStatus.BAD_REQUEST,
                        String.format("Unsupported API version: v%d. Supported versions: v1 to v%d",
                                version, GatewayConstants.MAX_API_VERSION));
            }

            exchange.getAttributes().put(API_VERSION_ATTR, version);
            log.debug("Resolved API version {} for path: {}", version, path);

            return chain.filter(exchange);
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
}
