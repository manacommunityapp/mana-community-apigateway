package com.manacommunity.gateway.controller;

import com.manacommunity.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/default")
    public Mono<ResponseEntity<ErrorResponse>> defaultFallbackGet(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "Service temporarily unavailable");
    }

    @PostMapping("/default")
    public Mono<ResponseEntity<ErrorResponse>> defaultFallbackPost(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "Service temporarily unavailable");
    }

    @GetMapping("/identity")
    public Mono<ResponseEntity<ErrorResponse>> identityFallbackGet(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "Identity service temporarily unavailable");
    }

    @PostMapping("/identity")
    public Mono<ResponseEntity<ErrorResponse>> identityFallbackPost(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "Identity service temporarily unavailable");
    }

    @GetMapping("/finance")
    public Mono<ResponseEntity<ErrorResponse>> financeFallbackGet(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "Finance service temporarily unavailable");
    }

    @PostMapping("/finance")
    public Mono<ResponseEntity<ErrorResponse>> financeFallbackPost(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "Finance service temporarily unavailable");
    }

    @GetMapping("/ai")
    public Mono<ResponseEntity<ErrorResponse>> aiFallbackGet(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "AI service temporarily unavailable");
    }

    @PostMapping("/ai")
    public Mono<ResponseEntity<ErrorResponse>> aiFallbackPost(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "AI service temporarily unavailable");
    }

    private Mono<ResponseEntity<ErrorResponse>> buildFallbackResponse(
            ServerWebExchange exchange, String message) {

        String correlationId = exchange.getAttribute("correlationId");
        String requestId = exchange.getAttribute("requestId");
        String path = exchange.getRequest().getPath().value();

        log.warn("Fallback triggered [correlationId={}, requestId={}, path={}]: {}",
                correlationId, requestId, path, message);

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                message,
                path,
                requestId
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse));
    }
}
