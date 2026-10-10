package com.manacommunity.gateway.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Route Registry — Gateway vs. Deployed Services Truth Map.
 * <p>
 * Maintains the explicit contract of which microservice owns which route prefix,
 * its runtime deployment port, verified controller endpoints, mandatory
 * verification requirements, and active health/readiness probe capabilities.
 */
@Slf4j
@Component
public class RouteRegistry {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisteredRoute {
        private String routeId;
        private String pathPattern;
        private String owningService;
        private String deployedServiceName;
        private int defaultPort;
        private String healthEndpoint;
        private String requiredVerification;
        private List<String> verificationItems;
        private List<String> supportedControllerPaths;
        private boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceHealthStatus {
        private String routeId;
        private String serviceName;
        private String status; // UP, DOWN, UNKNOWN
        private int port;
        private String endpoint;
        private long responseTimeMs;
        private String message;
        private Instant checkedAt;
    }

    private final Map<String, RegisteredRoute> registry = new ConcurrentHashMap<>();
    private final WebClient webClient;

    public RouteRegistry() {
        this(WebClient.builder());
    }

    public RouteRegistry(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder != null ? webClientBuilder.build() : WebClient.builder().build();
        initRegistry();
    }

    private void initRegistry() {
        // 1. Auth Service
        register(RegisteredRoute.builder()
                .routeId("auth-service")
                .pathPattern("/api/v1/auth/**")
                .owningService("Auth")
                .deployedServiceName("mana-community-auth")
                .defaultPort(8083)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Issuer, token, refresh and logout")
                .verificationItems(List.of(
                        "JWT issuer verification ('mana-community')",
                        "Token validation and 15-min expiration enforcement",
                        "Refresh token exchange and rotation",
                        "User logout and session token discarding audit"
                ))
                .supportedControllerPaths(List.of(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/auth/mfa/**",
                        "/api/auth/oidc/**",
                        "/api/otp/**"
                ))
                .active(true)
                .build());

        // 2. Sports Service
        register(RegisteredRoute.builder()
                .routeId("sports-service")
                .pathPattern("/api/v1/sports/**")
                .owningService("Sports")
                .deployedServiceName("mana-community-sports")
                .defaultPort(8082)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Tournament, auction and scoring")
                .verificationItems(List.of(
                        "Tournament creation, bracket and round-robin schedule generation",
                        "Player auction purse budgets, live bidding and team roster allocation",
                        "Real-time match scoring and leaderboard calculations"
                ))
                .supportedControllerPaths(List.of(
                        "/api/sports/**",
                        "/api/tournaments/**",
                        "/api/auction/config",
                        "/api/auction/live",
                        "/api/auction/players",
                        "/api/auction/teams",
                        "/api/venues/**"
                ))
                .active(true)
                .build());

        // 3. Events Service
        register(RegisteredRoute.builder()
                .routeId("events-service")
                .pathPattern("/api/v1/events/**")
                .owningService("Events")
                .deployedServiceName("mana-community-events")
                .defaultPort(8096)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Registration, permissions and notifications")
                .verificationItems(List.of(
                        "Event registration lifecycle (pending, confirmed, waitlisted)",
                        "RBAC permission validation ('Manage Events', 'Create Event')",
                        "Automated event notification dispatch upon state changes"
                ))
                .supportedControllerPaths(List.of(
                        "/api/events",
                        "/api/events/registrations",
                        "/api/events/{id}/notifications",
                        "/api/events/volunteers",
                        "/api/events/donations",
                        "/api/events/venues"
                ))
                .active(true)
                .build());

        // 4. Finance Service
        register(RegisteredRoute.builder()
                .routeId("finance-service")
                .pathPattern("/api/v1/finance/**")
                .owningService("Finance")
                .deployedServiceName("mana-community-finance")
                .defaultPort(8086)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Tenant isolation and financial integrity")
                .verificationItems(List.of(
                        "Tenant isolation across organization_id and community_id headers",
                        "Double-entry bookkeeping balance integrity (debits == credits)",
                        "Immutable invoices, receipts, and fiscal audit records"
                ))
                .supportedControllerPaths(List.of(
                        "/api/v1/finance/ledger",
                        "/api/finance/invoices",
                        "/api/finance/receipts",
                        "/api/finance/credit-notes",
                        "/api/finance/debit-notes",
                        "/api/finance/billing-cycles",
                        "/api/finance/budget"
                ))
                .active(true)
                .build());

        // 5. Payment Service
        register(RegisteredRoute.builder()
                .routeId("payment-service")
                .pathPattern("/api/v1/payment/**")
                .owningService("Payment")
                .deployedServiceName("mana-community-payment")
                .defaultPort(8087)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Signed webhooks and idempotency")
                .verificationItems(List.of(
                        "Cryptographic webhook signature verification (Razorpay / Stripe)",
                        "Idempotency-Key deduplication prevents duplicate processing",
                        "Escrow funds hold, lock, and split payment releases"
                ))
                .supportedControllerPaths(List.of(
                        "/api/payments/intents",
                        "/api/payments/intents/{id}/confirm",
                        "/api/payments/webhooks",
                        "/api/payments/intents/{id}/escrow",
                        "/api/payments/refunds"
                ))
                .active(true)
                .build());

        // 6. Marketplace Service
        register(RegisteredRoute.builder()
                .routeId("marketplace-service")
                .pathPattern("/api/v1/marketplace/**")
                .owningService("Marketplace")
                .deployedServiceName("mana-community-marketplace")
                .defaultPort(8085)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Orders, payments and refunds")
                .verificationItems(List.of(
                        "Cart checkout to Order placement transaction lifecycle",
                        "Buyer-seller escrow payment locking on order creation",
                        "Dispute raising, seller resolution, and refund processing"
                ))
                .supportedControllerPaths(List.of(
                        "/api/v1/marketplace/orders",
                        "/api/v1/marketplace/listings",
                        "/api/v1/marketplace/cart",
                        "/api/v1/marketplace/disputes",
                        "/api/v1/marketplace/reviews",
                        "/api/v1/marketplace/sellers"
                ))
                .active(true)
                .build());

        // 7. Notification Service
        register(RegisteredRoute.builder()
                .routeId("notification-service")
                .pathPattern("/api/v1/notifications/**")
                .owningService("Notification")
                .deployedServiceName("mana-community-notification")
                .defaultPort(8093)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Event delivery, retries and preferences")
                .verificationItems(List.of(
                        "Multi-channel event dispatch (Email, SMS, Push, WebSocket)",
                        "Exponential backoff retry policy and Dead Letter Queue (DLQ) ingestion",
                        "User notification opt-out preferences and quiet-hours suppression"
                ))
                .supportedControllerPaths(List.of(
                        "/api/notifications",
                        "/api/notifications/events",
                        "/api/notifications/webhooks",
                        "/api/notifications/dlq",
                        "/api/notifications/templates"
                ))
                .active(true)
                .build());

        // 8. Food Service
        register(RegisteredRoute.builder()
                .routeId("food-service")
                .pathPattern("/api/v1/food/**")
                .owningService("Food")
                .deployedServiceName("mana-community-food")
                .defaultPort(8088)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Meal ordering, menu catalog and vendor kitchen dispatch")
                .verificationItems(List.of(
                        "Menu catalog caching and diet tagging",
                        "Order placement with real-time status tracking",
                        "Kitchen vendor fulfillment notification"
                ))
                .supportedControllerPaths(List.of("/api/v1/food/**", "/api/food/**"))
                .active(true)
                .build());

        // 9. Media Service
        register(RegisteredRoute.builder()
                .routeId("media-service")
                .pathPattern("/api/v1/media/**")
                .owningService("Media")
                .deployedServiceName("mana-community-media")
                .defaultPort(8084)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Media upload, S3 pre-signed URLs and thumbnail transcoding")
                .verificationItems(List.of(
                        "Pre-signed upload URL generation with TTL",
                        "Virus scanning and MIME-type verification",
                        "Image thumbnail and video preview transcoding"
                ))
                .supportedControllerPaths(List.of("/api/v1/media/**", "/api/media/**"))
                .active(true)
                .build());

        // 10. Professional Network Service
        register(RegisteredRoute.builder()
                .routeId("professional-network-service")
                .pathPattern("/api/v1/professional-network/**")
                .owningService("Professional Network")
                .deployedServiceName("mana-community-professional-network")
                .defaultPort(8091)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Resident profile, business directory and job listings")
                .verificationItems(List.of(
                        "Resident professional profile search with Elasticsearch",
                        "Community business directory listing approval",
                        "Job recommendation feed"
                ))
                .supportedControllerPaths(List.of("/api/v1/professional-network/**", "/api/professional-network/**"))
                .active(true)
                .build());

        // 11. Resource Booking Service
        register(RegisteredRoute.builder()
                .routeId("booking-service")
                .pathPattern("/api/v1/booking/**")
                .owningService("Resource Booking")
                .deployedServiceName("mana-community-resource-booking")
                .defaultPort(8089)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Amenity booking, slot locking and deposit calculation")
                .verificationItems(List.of(
                        "Clubhouse and facility slot availability checking",
                        "Pessimistic locking preventing double booking",
                        "Refundable deposit tracking"
                ))
                .supportedControllerPaths(List.of("/api/v1/booking/**", "/api/booking/**", "/api/resource-booking/**"))
                .active(true)
                .build());

        // 12. Services Platform Service
        register(RegisteredRoute.builder()
                .routeId("services-platform-service")
                .pathPattern("/api/v1/services/**")
                .owningService("Services Platform")
                .deployedServiceName("mana-community-services-platform")
                .defaultPort(8104)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Service requests, provider dispatch and customer sign-off")
                .verificationItems(List.of(
                        "Resident home maintenance request dispatch",
                        "Service provider arrival OTP verification",
                        "Digital completion sign-off and rating"
                ))
                .supportedControllerPaths(List.of("/api/v1/services/**", "/api/services/**", "/api/services-platform/**"))
                .active(true)
                .build());

        // 13. Social Service
        register(RegisteredRoute.builder()
                .routeId("social-service")
                .pathPattern("/api/v1/social/**")
                .owningService("Social")
                .deployedServiceName("mana-community-social")
                .defaultPort(8092)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Feed posts, community discussions and content moderation")
                .verificationItems(List.of(
                        "Feed timeline pagination and user mentions",
                        "Automated toxicity and profanity filter",
                        "Moderator takedown action logging"
                ))
                .supportedControllerPaths(List.of("/api/v1/social/**", "/api/social/**"))
                .active(true)
                .build());

        // 14. Vendor Service
        register(RegisteredRoute.builder()
                .routeId("vendor-service")
                .pathPattern("/api/v1/vendor/**")
                .owningService("Vendor")
                .deployedServiceName("mana-community-vendor")
                .defaultPort(8090)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Vendor onboarding, KYC, work orders and SLA tracking")
                .verificationItems(List.of(
                        "Vendor identity document verification",
                        "Maintenance work order contract assignment",
                        "SLA breach detection and penalty accrual"
                ))
                .supportedControllerPaths(List.of("/api/v1/vendor/**", "/api/vendor/**"))
                .active(true)
                .build());

        // 15. Wallet Service
        register(RegisteredRoute.builder()
                .routeId("wallet-service")
                .pathPattern("/api/v1/wallet/**")
                .owningService("Wallet")
                .deployedServiceName("mana-community-wallet")
                .defaultPort(8094)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Community prepaid wallet balances and QR code top-ups")
                .verificationItems(List.of(
                        "Double-entry wallet credit and debit balance checks",
                        "Dynamic QR payment token generation",
                        "Daily spending limits and transaction PIN validation"
                ))
                .supportedControllerPaths(List.of("/api/v1/wallet/**", "/api/wallet/**"))
                .active(true)
                .build());

        // 16. Emergency Service
        register(RegisteredRoute.builder()
                .routeId("emergency-service")
                .pathPattern("/api/v1/emergency/**")
                .owningService("Emergency")
                .deployedServiceName("mana-community-emergency")
                .defaultPort(8095)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Resident SOS alert broadcast, security guard dispatch and audit log")
                .verificationItems(List.of(
                        "Sub-second SOS broadcast to community security guard consoles",
                        "Emergency location pinpointing",
                        "Incident resolution audit log"
                ))
                .supportedControllerPaths(List.of("/api/v1/emergency/**", "/api/emergency/**"))
                .active(true)
                .build());

        // 17. Group Marketplace Service
        register(RegisteredRoute.builder()
                .routeId("group-marketplace-service")
                .pathPattern("/api/v1/group-marketplace/**")
                .owningService("Group Marketplace")
                .deployedServiceName("mana-community-group-marketplace")
                .defaultPort(8105)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Bulk resident group buying deals and minimum order quorum")
                .verificationItems(List.of(
                        "Group deal countdown and quorum tracking",
                        "Pre-authorization hold on buyer cards",
                        "Automated refund triggers if quorum fails"
                ))
                .supportedControllerPaths(List.of("/api/v1/group-marketplace/**", "/api/group-marketplace/**"))
                .active(true)
                .build());

        // 18. Travel Service
        register(RegisteredRoute.builder()
                .routeId("travel-service")
                .pathPattern("/api/v1/travel/**")
                .owningService("Travel")
                .deployedServiceName("mana-community-travel")
                .defaultPort(8097)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Carpooling, resident shuttle trips and QR ticketing")
                .verificationItems(List.of(
                        "Resident carpool route matching",
                        "Community shuttle bus timetable tracking",
                        "QR boarding ticket validation"
                ))
                .supportedControllerPaths(List.of("/api/v1/travel/**", "/api/travel/**", "/api/v1/trips/**", "/api/trips/**"))
                .active(true)
                .build());

        // 19. Helpdesk Service
        register(RegisteredRoute.builder()
                .routeId("helpdesk-service")
                .pathPattern("/api/v1/helpdesk/**")
                .owningService("Helpdesk")
                .deployedServiceName("mana-community-helpdesk")
                .defaultPort(8098)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Facility maintenance tickets, SLA escalation and resolution")
                .verificationItems(List.of(
                        "Ticket creation with photo attachment",
                        "Automatic technician assignment based on skill and availability",
                        "SLA countdown and supervisor escalation"
                ))
                .supportedControllerPaths(List.of("/api/v1/helpdesk/**", "/api/helpdesk/**"))
                .active(true)
                .build());

        // 20. Graph Service
        register(RegisteredRoute.builder()
                .routeId("graph-service")
                .pathPattern("/api/v1/graph/**")
                .owningService("Graph")
                .deployedServiceName("mana-community-graph")
                .defaultPort(8099)
                .healthEndpoint("/actuator/health")
                .requiredVerification("Community relationship graphs and resident recommendations")
                .verificationItems(List.of(
                        "Community social connection graph traversals",
                        "Interest group recommendations",
                        "Privacy boundaries for non-friends"
                ))
                .supportedControllerPaths(List.of("/api/v1/graph/**", "/api/graph/**", "/community-graph/**"))
                .active(true)
                .build());

        log.info("Initialized RouteRegistry with {} platform verified routes", registry.size());
    }

    public void register(RegisteredRoute route) {
        registry.put(route.getRouteId(), route);
    }

    public List<RegisteredRoute> getAllRoutes() {
        return new ArrayList<>(registry.values());
    }

    public Optional<RegisteredRoute> getRoute(String routeId) {
        return Optional.ofNullable(registry.get(routeId));
    }

    public Optional<RegisteredRoute> findByOwningService(String serviceName) {
        return registry.values().stream()
                .filter(r -> r.getOwningService().equalsIgnoreCase(serviceName) ||
                             r.getRouteId().equalsIgnoreCase(serviceName) ||
                             r.getDeployedServiceName().equalsIgnoreCase(serviceName))
                .findFirst();
    }

    public Optional<RegisteredRoute> findMatchingRoute(String path) {
        return registry.values().stream()
                .filter(r -> {
                    String prefix = r.getPathPattern().replace("/**", "");
                    return path.startsWith(prefix) || r.getSupportedControllerPaths().stream().anyMatch(path::startsWith);
                })
                .findFirst();
    }

    public Map<String, Object> getRegistrySummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRoutes", registry.size());
        summary.put("routes", getAllRoutes());
        return summary;
    }

    /**
     * Probes the health/readiness of a single registered downstream service.
     */
    public Mono<ServiceHealthStatus> checkServiceHealth(RegisteredRoute route) {
        String healthUrl = "http://localhost:" + route.getDefaultPort() + route.getHealthEndpoint();
        long start = System.currentTimeMillis();
        return webClient.get()
                .uri(healthUrl)
                .retrieve()
                .toBodilessEntity()
                .map(response -> ServiceHealthStatus.builder()
                        .routeId(route.getRouteId())
                        .serviceName(route.getOwningService())
                        .status(response.getStatusCode().is2xxSuccessful() ? "UP" : "DOWN")
                        .port(route.getDefaultPort())
                        .endpoint(route.getHealthEndpoint())
                        .responseTimeMs(System.currentTimeMillis() - start)
                        .message("HTTP " + response.getStatusCode().value())
                        .checkedAt(Instant.now())
                        .build())
                .timeout(Duration.ofMillis(1200))
                .onErrorResume(ex -> Mono.just(ServiceHealthStatus.builder()
                        .routeId(route.getRouteId())
                        .serviceName(route.getOwningService())
                        .status("DOWN")
                        .port(route.getDefaultPort())
                        .endpoint(route.getHealthEndpoint())
                        .responseTimeMs(System.currentTimeMillis() - start)
                        .message(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())
                        .checkedAt(Instant.now())
                        .build()));
    }

    /**
     * Probes the health and readiness across all registered downstream services.
     */
    public Mono<Map<String, Object>> checkAllServices() {
        return Flux.fromIterable(registry.values())
                .flatMap(this::checkServiceHealth)
                .collectList()
                .map(statuses -> {
                    long upCount = statuses.stream().filter(s -> "UP".equalsIgnoreCase(s.getStatus())).count();
                    String overall = upCount == statuses.size() ? "UP" : (upCount > 0 ? "DEGRADED" : "DOWN");
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", overall);
                    result.put("totalServices", statuses.size());
                    result.put("upServices", upCount);
                    result.put("downServices", statuses.size() - upCount);
                    result.put("services", statuses);
                    result.put("timestamp", Instant.now().toString());
                    return result;
                });
    }
}