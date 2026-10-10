package com.manacommunity.gateway.routing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST endpoint exposing the Gateway Route Registry, service verification contracts,
 * and downstream service health/readiness probe results.
 */
@RestController
@RequestMapping("/gateway/routes")
@RequiredArgsConstructor
public class RouteRegistryController {

    private final RouteRegistry routeRegistry;

    @GetMapping("/registry")
    public Mono<ResponseEntity<Map<String, Object>>> getRegistry() {
        return Mono.just(ResponseEntity.ok(routeRegistry.getRegistrySummary()));
    }

    @GetMapping("/registry/{service}")
    public Mono<ResponseEntity<RouteRegistry.RegisteredRoute>> getServiceRoute(@PathVariable String service) {
        return Mono.just(routeRegistry.findByOwningService(service)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build()));
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> getAllServicesHealth() {
        return routeRegistry.checkAllServices()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/readiness")
    public Mono<ResponseEntity<Map<String, Object>>> getPlatformReadiness() {
        return routeRegistry.checkAllServices()
                .map(result -> {
                    String status = (String) result.getOrDefault("status", "DOWN");
                    if ("UP".equalsIgnoreCase(status)) {
                        return ResponseEntity.ok(result);
                    } else if ("DEGRADED".equalsIgnoreCase(status)) {
                        return ResponseEntity.status(200).body(result);
                    } else {
                        return ResponseEntity.status(503).body(result);
                    }
                });
    }

    @GetMapping("/health/{service}")
    public Mono<ResponseEntity<RouteRegistry.ServiceHealthStatus>> getServiceHealth(@PathVariable String service) {
        return Mono.justOrEmpty(routeRegistry.findByOwningService(service))
                .flatMap(routeRegistry::checkServiceHealth)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}