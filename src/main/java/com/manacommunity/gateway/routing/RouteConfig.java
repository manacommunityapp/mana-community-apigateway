package com.manacommunity.gateway.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route configuration for the API Gateway.
 * <p>
 * Primary route definitions live in {@code application.yml} and are loaded automatically
 * by Spring Cloud Gateway. This class provides a supplementary {@link RouteLocator} bean
 * that can be used for:
 * <ul>
 *   <li>Programmatic route customization that cannot be expressed in YAML</li>
 *   <li>Adding metadata to routes for Swagger/OpenAPI discovery</li>
 *   <li>Conditional routes that depend on runtime configuration</li>
 * </ul>
 * <p>
 * The YAML-defined routes cover all microservice paths (identity, resident, property,
 * finance, marketplace, food, healthcare, sports, events, security, visitor, booking,
 * notification, workflow, document, search, analytics, ai) with CircuitBreaker and
 * Retry filters configured per service.
 */
@Slf4j
@Configuration
public class RouteConfig {

    /**
     * Supplementary route locator for programmatic route additions.
     * Routes defined here merge with YAML-defined routes. The YAML routes
     * take precedence when predicates overlap.
     * <p>
     * This bean also serves as a hook for Swagger/Springdoc discovery of gateway routes.
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("Initializing custom route locator (primary routes are YAML-defined)");

        return builder.routes()
                // Health-check route for the gateway itself (not proxied)
                .route("gateway-health", r -> r
                        .path("/gateway/health")
                        .filters(f -> f
                                .setPath("/actuator/health")
                        )
                        .metadata("apiTitle", "Gateway Health")
                        .metadata("apiDescription", "Health check endpoint for the API Gateway")
                        .uri("forward:///actuator/health")
                )
                .build();
    }
}
