package com.manacommunity.gateway.routing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST endpoint exposing the Gateway Route Registry, service verification contracts,
 * downstream service health/readiness probe results, and the machine-readable
 * service inventory with anomaly detection capabilities.
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

    /**
     * Returns the full machine-readable service inventory including owners,
     * API versions, database ownership, dependencies and gateway route IDs.
     */
    @GetMapping("/inventory")
    public Mono<ResponseEntity<Map<String, RouteRegistry.ServiceInventoryEntry>>> getServiceInventory() {
        return Mono.just(ResponseEntity.ok(routeRegistry.getMachineReadableInventory()));
    }

    /**
     * Returns an anomaly report detailing duplicated endpoints, port collisions,
     * and orphaned routes that lack a registered backend.
     */
    @GetMapping("/anomalies")
    public Mono<ResponseEntity<RouteRegistry.RegistryAnomalyReport>> getAnomalyReport() {
        return Mono.just(ResponseEntity.ok(routeRegistry.generateAnomalyReport()));
    }

    /**
     * Returns the inter-service dependency graph (serviceId -> internal dependency list).
     */
    @GetMapping("/dependencies")
    public Mono<ResponseEntity<Map<String, List<String>>>> getDependencyGraph() {
        return Mono.just(ResponseEntity.ok(routeRegistry.getDependencyGraph()));
    }

    /**
     * Returns database ownership map (serviceId -> database name, type, primary entities).
     */
    @GetMapping("/databases")
    public Mono<ResponseEntity<Map<String, RouteRegistry.DatabaseOwnership>>> getDatabaseOwnership() {
        return Mono.just(ResponseEntity.ok(routeRegistry.getDatabaseOwnershipMap()));
    }
}