package com.manacommunity.gateway.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Validates request headers for correctness and security:
 * <ul>
 *   <li>Enforces Content-Type on POST/PUT/PATCH requests</li>
 *   <li>Strips X-Internal-* headers to prevent header injection from external clients</li>
 * </ul>
 */
@Slf4j
@Component
public class HeaderValidationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -70;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INTERNAL_HEADER_PREFIX = "X-Internal-";

    private static final Set<HttpMethod> METHODS_REQUIRING_CONTENT_TYPE = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.APPLICATION_JSON_VALUE.toLowerCase(),
            MediaType.MULTIPART_FORM_DATA_VALUE.toLowerCase()
    );

    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();

        // Validate Content-Type for write methods
        if (method != null && METHODS_REQUIRING_CONTENT_TYPE.contains(method)) {
            MediaType contentType = request.getHeaders().getContentType();

            if (contentType == null) {
                log.warn("Missing Content-Type header for {} request to {}",
                        method, request.getURI().getPath());
                return writeErrorResponse(exchange, HttpStatus.BAD_REQUEST,
                        "Content-Type header is required for " + method + " requests");
            }

            String baseContentType = contentType.getType() + "/" + contentType.getSubtype();
            if (!ALLOWED_CONTENT_TYPES.contains(baseContentType.toLowerCase())) {
                log.warn("Unsupported Content-Type [{}] for {} request to {}",
                        contentType, method, request.getURI().getPath());
                return writeErrorResponse(exchange, HttpStatus.BAD_REQUEST,
                        "Unsupported Content-Type. Allowed types: application/json, multipart/form-data");
            }
        }

        // Strip X-Internal-* headers to prevent external header injection
        List<String> internalHeaders = request.getHeaders().keySet().stream()
                .filter(header -> header.startsWith(INTERNAL_HEADER_PREFIX))
                .toList();

        if (!internalHeaders.isEmpty()) {
            log.warn("Stripping {} reserved internal header(s) from request to {}",
                    internalHeaders.size(), request.getURI().getPath());

            ServerHttpRequest.Builder requestBuilder = request.mutate();
            for (String header : internalHeaders) {
                requestBuilder.headers(h -> h.remove(header));
            }

            ServerHttpRequest sanitizedRequest = requestBuilder.build();
            ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();
            return chain.filter(sanitizedExchange);
        }

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
