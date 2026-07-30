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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Rejects requests whose Content-Length exceeds the configured maximum size
 * with a 413 Payload Too Large response.
 */
@Slf4j
@Component
public class RequestSizeFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -80;
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
        String contentLengthHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);

        if (contentLengthHeader != null) {
            try {
                long contentLength = Long.parseLong(contentLengthHeader);
                if (contentLength > GatewayConstants.MAX_REQUEST_SIZE_BYTES) {
                    log.warn("Request rejected: payload size {} bytes exceeds maximum {} bytes. path={}",
                            contentLength,
                            GatewayConstants.MAX_REQUEST_SIZE_BYTES,
                            exchange.getRequest().getURI().getPath());

                    return writeErrorResponse(exchange, HttpStatus.PAYLOAD_TOO_LARGE,
                            String.format("Request payload exceeds the maximum allowed size of %d bytes",
                                    GatewayConstants.MAX_REQUEST_SIZE_BYTES));
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid Content-Length header value: {}", contentLengthHeader);
                return writeErrorResponse(exchange, HttpStatus.BAD_REQUEST,
                        "Invalid Content-Length header");
            }
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
