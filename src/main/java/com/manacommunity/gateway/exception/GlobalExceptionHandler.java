package com.manacommunity.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.manacommunity.gateway.dto.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@Order(-1)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String correlationId = exchange.getAttribute("correlationId");
        String requestId = exchange.getAttribute("requestId");
        String path = exchange.getRequest().getPath().value();

        HttpStatus httpStatus;
        String error;
        String message;

        if (ex instanceof GatewayException gatewayEx) {
            httpStatus = HttpStatus.resolve(gatewayEx.getStatus());
            if (httpStatus == null) {
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            error = gatewayEx.getError();
            message = gatewayEx.getMessage();
            log.warn("Gateway exception [correlationId={}]: status={}, message={}",
                    correlationId, httpStatus.value(), message);

        } else if (ex instanceof ResponseStatusException responseStatusEx) {
            httpStatus = HttpStatus.resolve(responseStatusEx.getStatusCode().value());
            if (httpStatus == null) {
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            error = httpStatus.getReasonPhrase();
            message = responseStatusEx.getReason() != null
                    ? responseStatusEx.getReason()
                    : httpStatus.getReasonPhrase();
            log.warn("Response status exception [correlationId={}]: status={}, message={}",
                    correlationId, httpStatus.value(), message);

        } else if (ex instanceof ConnectException) {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            error = httpStatus.getReasonPhrase();
            message = "Service unavailable";
            log.error("Connection exception [correlationId={}]: {}", correlationId, ex.getMessage());

        } else if (ex instanceof TimeoutException) {
            httpStatus = HttpStatus.GATEWAY_TIMEOUT;
            error = httpStatus.getReasonPhrase();
            message = "Gateway timeout";
            log.error("Timeout exception [correlationId={}]: {}", correlationId, ex.getMessage());

        } else if (ex instanceof CallNotPermittedException) {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            error = httpStatus.getReasonPhrase();
            message = "Service temporarily unavailable";
            log.warn("Circuit breaker open [correlationId={}]: {}", correlationId, ex.getMessage());

        } else if (ex instanceof JwtException) {
            httpStatus = HttpStatus.UNAUTHORIZED;
            error = httpStatus.getReasonPhrase();
            message = "Unauthorized";
            log.warn("JWT exception [correlationId={}]: {}", correlationId, ex.getMessage());

        } else {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            error = httpStatus.getReasonPhrase();
            message = "Internal server error";
            log.error("Unhandled exception [correlationId={}]: {}", correlationId, ex.getMessage(), ex);
        }

        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = ErrorResponse.of(
                httpStatus.value(),
                error,
                message,
                path,
                requestId
        );

        return writeResponse(response, errorResponse);
    }

    private Mono<Void> writeResponse(ServerHttpResponse response, ErrorResponse errorResponse) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBufferFactory bufferFactory = response.bufferFactory();
            DataBuffer dataBuffer = bufferFactory.wrap(bytes);
            return response.writeWith(Mono.just(dataBuffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            String fallback = "{\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"Internal server error\"}";
            DataBufferFactory bufferFactory = response.bufferFactory();
            DataBuffer dataBuffer = bufferFactory.wrap(fallback.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(dataBuffer));
        }
    }
}
