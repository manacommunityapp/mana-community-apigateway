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
 * Route Registry -- Gateway vs. Deployed Services Truth Map.
 * <p>
 * Maintains the explicit contract of which microservice owns which route prefix,
 * its runtime deployment port, verified controller endpoints, mandatory
 * verification requirements, and active health/readiness probe capabilities.
 * <p>
 * Also provides machine-readable service inventory with database ownership,
 * dependency graphs, and automatic detection of orphaned routes and duplicated
 * endpoints across the platform.
 */
@Slf4j
@Component
public class RouteRegistry {

    // -------------------------------------------------------------------------
    // Domain Models
    // -------------------------------------------------------------------------

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseOwnership {
        private String databaseName;
        private String type;
        private List<String> primaryEntities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceInventoryEntry {
        private String serviceId;
        private String displayName;
        private String owningTeam;
        private String ownerEmail;
        private String deployedServiceName;
        private int defaultPort;
        private List<String> apiVersions;
        private DatabaseOwnership databaseOwnership;
        private List<String> internalDependencies;
        private List<String> externalDependencies;
        private List<String> gatewayRouteIds;
        private List<String> pathPatterns;
        private String healthEndpoint;
        private boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateEndpoint {
        private String pathPattern;
        private List<String> conflictingRouteIds;
        private String conflictType; // EXACT_DUPLICATE, OVERLAPPING_WILDCARD, PORT_COLLISION
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrphanedRoute {
        private String routeId;
        private String pathPattern;
        private String reason; // NO_REGISTERED_BACKEND, INACTIVE_SERVICE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegistryAnomalyReport {
        private List<DuplicateEndpoint> duplicatedEndpoints;
        private List<OrphanedRoute> orphanedRoutes;
        private int totalAnomalies;
        private String overallHealth; // HEALTHY, WARNING, CRITICAL
        private Instant generatedAt;
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Map<String, RegisteredRoute> registry = new ConcurrentHashMap<>();
    private final Map<String, ServiceInventoryEntry> inventory = new ConcurrentHashMap<>();

    /** Gateway route IDs present in application.yml but NOT backed by a registered service. */
    private static final Set<String> GATEWAY_ONLY_ROUTE_IDS = Set.of(
            "trips-legacy",
            "parking-monolith",
            "resident-service",
            "property-service",
            "security-service",
            "visitor-service",
            "workflow-service",
            "document-service",
            "search-service",
            "analytics-service"
    );

    private final WebClient webClient;

    public RouteRegistry() {
        this(WebClient.builder());
    }

    public RouteRegistry(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder != null ? webClientBuilder.build() : WebClient.builder().build();
        initRegistry();
        initInventory();
    }

    // -------------------------------------------------------------------------
    // Route Registry Initialization
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Service Inventory Initialization
    // -------------------------------------------------------------------------

    private void initInventory() {
        addInventory(ServiceInventoryEntry.builder()
                .serviceId("auth-service").displayName("Authentication Service")
                .owningTeam("Platform Security").ownerEmail("platform-security@manacommunity.app")
                .deployedServiceName("mana-community-auth").defaultPort(8083).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_auth_db").type("PostgreSQL")
                        .primaryEntities(List.of("users", "sessions", "refresh_tokens", "otp_records", "mfa_configs")).build())
                .internalDependencies(List.of("notification-service"))
                .externalDependencies(List.of("Redis", "Kafka"))
                .gatewayRouteIds(List.of("auth-service")).pathPatterns(List.of("/api/v1/auth/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("sports-service").displayName("Sports and Tournament Service")
                .owningTeam("Community Activities").ownerEmail("sports-team@manacommunity.app")
                .deployedServiceName("mana-community-sports").defaultPort(8082).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_sports_db").type("PostgreSQL")
                        .primaryEntities(List.of("tournaments", "teams", "players", "matches", "auctions", "venues")).build())
                .internalDependencies(List.of("notification-service", "payment-service", "media-service"))
                .externalDependencies(List.of("Redis", "Kafka"))
                .gatewayRouteIds(List.of("sports-service")).pathPatterns(List.of("/api/v1/sports/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("events-service").displayName("Events and Community Gatherings Service")
                .owningTeam("Community Activities").ownerEmail("events-team@manacommunity.app")
                .deployedServiceName("mana-community-events").defaultPort(8096).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_events_db").type("PostgreSQL")
                        .primaryEntities(List.of("events", "registrations", "volunteers", "donations", "venues")).build())
                .internalDependencies(List.of("notification-service", "payment-service", "media-service"))
                .externalDependencies(List.of("Redis", "Kafka"))
                .gatewayRouteIds(List.of("events-service")).pathPatterns(List.of("/api/v1/events/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("finance-service").displayName("Finance and Accounting Service")
                .owningTeam("Finance Engineering").ownerEmail("finance-engineering@manacommunity.app")
                .deployedServiceName("mana-community-finance").defaultPort(8086).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_finance_db").type("PostgreSQL")
                        .primaryEntities(List.of("ledger_entries", "invoices", "receipts", "billing_cycles", "budgets")).build())
                .internalDependencies(List.of("payment-service", "notification-service"))
                .externalDependencies(List.of("Redis", "Kafka"))
                .gatewayRouteIds(List.of("finance-service")).pathPatterns(List.of("/api/v1/finance/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("payment-service").displayName("Payment Processing Service")
                .owningTeam("Finance Engineering").ownerEmail("payments@manacommunity.app")
                .deployedServiceName("mana-community-payment").defaultPort(8087).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_payment_db").type("PostgreSQL")
                        .primaryEntities(List.of("payment_intents", "transactions", "refunds", "escrow_holds", "webhooks")).build())
                .internalDependencies(List.of("wallet-service", "notification-service", "finance-service"))
                .externalDependencies(List.of("Stripe", "Razorpay", "Redis", "Kafka"))
                .gatewayRouteIds(List.of("payment-service")).pathPatterns(List.of("/api/v1/payment/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("marketplace-service").displayName("Community Marketplace Service")
                .owningTeam("Commerce Platform").ownerEmail("marketplace@manacommunity.app")
                .deployedServiceName("mana-community-marketplace").defaultPort(8085).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_marketplace_db").type("PostgreSQL")
                        .primaryEntities(List.of("listings", "orders", "cart_items", "disputes", "seller_profiles")).build())
                .internalDependencies(List.of("payment-service", "notification-service", "media-service", "wallet-service"))
                .externalDependencies(List.of("Redis", "Kafka", "Elasticsearch"))
                .gatewayRouteIds(List.of("marketplace-service")).pathPatterns(List.of("/api/v1/marketplace/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("notification-service").displayName("Notification and Alert Service")
                .owningTeam("Platform Infra").ownerEmail("notifications@manacommunity.app")
                .deployedServiceName("mana-community-notification").defaultPort(8093).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_notification_db").type("PostgreSQL")
                        .primaryEntities(List.of("notifications", "templates", "dlq_messages", "user_preferences")).build())
                .internalDependencies(List.of())
                .externalDependencies(List.of("Firebase FCM", "AWS SES", "Twilio SMS", "Redis", "Kafka"))
                .gatewayRouteIds(List.of("notification-service")).pathPatterns(List.of("/api/v1/notifications/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("food-service").displayName("Food and Dining Service")
                .owningTeam("Commerce Platform").ownerEmail("food-team@manacommunity.app")
                .deployedServiceName("mana-community-food").defaultPort(8088).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_food_db").type("PostgreSQL")
                        .primaryEntities(List.of("menus", "menu_items", "food_orders", "kitchens", "diet_tags")).build())
                .internalDependencies(List.of("payment-service", "notification-service", "media-service"))
                .externalDependencies(List.of("Redis", "Kafka"))
                .gatewayRouteIds(List.of("food-service")).pathPatterns(List.of("/api/v1/food/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("media-service").displayName("Media and File Service")
                .owningTeam("Platform Infra").ownerEmail("media-team@manacommunity.app")
                .deployedServiceName("mana-community-media").defaultPort(8084).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_media_db").type("PostgreSQL")
                        .primaryEntities(List.of("media_assets", "upload_requests", "thumbnails", "virus_scan_results")).build())
                .internalDependencies(List.of())
                .externalDependencies(List.of("AWS S3", "AWS Lambda", "Redis"))
                .gatewayRouteIds(List.of("media-service")).pathPatterns(List.of("/api/v1/media/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("professional-network-service").displayName("Professional Network Service")
                .owningTeam("Community Social").ownerEmail("professional-network@manacommunity.app")
                .deployedServiceName("mana-community-professional-network").defaultPort(8091).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_professional_db").type("PostgreSQL")
                        .primaryEntities(List.of("professional_profiles", "business_listings", "job_posts", "connections")).build())
                .internalDependencies(List.of("media-service", "notification-service"))
                .externalDependencies(List.of("Elasticsearch", "Redis", "Kafka"))
                .gatewayRouteIds(List.of("professional-network-service")).pathPatterns(List.of("/api/v1/professional-network/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("booking-service").displayName("Resource Booking Service")
                .owningTeam("Facilities Management").ownerEmail("bookings@manacommunity.app")
                .deployedServiceName("mana-community-resource-booking").defaultPort(8089).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_booking_db").type("PostgreSQL")
                        .primaryEntities(List.of("amenities", "booking_slots", "booking_requests", "deposits")).build())
                .internalDependencies(List.of("payment-service", "notification-service", "finance-service"))
                .externalDependencies(List.of("Redis"))
                .gatewayRouteIds(List.of("booking-service")).pathPatterns(List.of("/api/v1/booking/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("services-platform-service").displayName("Home Services Platform Service")
                .owningTeam("Facilities Management").ownerEmail("services-platform@manacommunity.app")
                .deployedServiceName("mana-community-services-platform").defaultPort(8104).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_services_db").type("PostgreSQL")
                        .primaryEntities(List.of("service_requests", "service_providers", "service_categories", "work_orders")).build())
                .internalDependencies(List.of("vendor-service", "notification-service", "payment-service"))
                .externalDependencies(List.of("Redis", "Kafka"))
                .gatewayRouteIds(List.of("services-platform-service")).pathPatterns(List.of("/api/v1/services/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("social-service").displayName("Social and Community Feed Service")
                .owningTeam("Community Social").ownerEmail("social-team@manacommunity.app")
                .deployedServiceName("mana-community-social").defaultPort(8092).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_social_db").type("PostgreSQL")
                        .primaryEntities(List.of("posts", "comments", "reactions", "media_attachments", "moderation_logs")).build())
                .internalDependencies(List.of("media-service", "notification-service"))
                .externalDependencies(List.of("Redis", "Kafka", "Elasticsearch"))
                .gatewayRouteIds(List.of("social-service")).pathPatterns(List.of("/api/v1/social/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("vendor-service").displayName("Vendor and Contractor Management Service")
                .owningTeam("Facilities Management").ownerEmail("vendors@manacommunity.app")
                .deployedServiceName("mana-community-vendor").defaultPort(8090).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_vendor_db").type("PostgreSQL")
                        .primaryEntities(List.of("vendors", "kyc_documents", "work_orders", "sla_contracts", "penalties")).build())
                .internalDependencies(List.of("notification-service", "payment-service"))
                .externalDependencies(List.of("Redis", "Kafka"))
                .gatewayRouteIds(List.of("vendor-service")).pathPatterns(List.of("/api/v1/vendor/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("wallet-service").displayName("Community Wallet Service")
                .owningTeam("Finance Engineering").ownerEmail("wallet@manacommunity.app")
                .deployedServiceName("mana-community-wallet").defaultPort(8094).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_wallet_db").type("PostgreSQL")
                        .primaryEntities(List.of("wallet_accounts", "wallet_transactions", "qr_tokens", "spending_limits")).build())
                .internalDependencies(List.of("payment-service", "notification-service"))
                .externalDependencies(List.of("Redis"))
                .gatewayRouteIds(List.of("wallet-service")).pathPatterns(List.of("/api/v1/wallet/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("emergency-service").displayName("Emergency and SOS Service")
                .owningTeam("Safety and Security").ownerEmail("emergency@manacommunity.app")
                .deployedServiceName("mana-community-emergency").defaultPort(8095).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_emergency_db").type("PostgreSQL")
                        .primaryEntities(List.of("alerts", "incidents", "responders", "audit_logs", "escalation_chains")).build())
                .internalDependencies(List.of("notification-service"))
                .externalDependencies(List.of("Redis", "Kafka", "WebSocket"))
                .gatewayRouteIds(List.of("emergency-service")).pathPatterns(List.of("/api/v1/emergency/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("group-marketplace-service").displayName("Group Buying Marketplace Service")
                .owningTeam("Commerce Platform").ownerEmail("group-marketplace@manacommunity.app")
                .deployedServiceName("mana-community-group-marketplace").defaultPort(8105).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_group_marketplace_db").type("PostgreSQL")
                        .primaryEntities(List.of("group_deals", "deal_participants", "quorum_records", "pre_auth_holds")).build())
                .internalDependencies(List.of("payment-service", "notification-service", "marketplace-service"))
                .externalDependencies(List.of("Redis", "Kafka"))
                .gatewayRouteIds(List.of("group-marketplace-service")).pathPatterns(List.of("/api/v1/group-marketplace/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("travel-service").displayName("Travel and Carpool Service")
                .owningTeam("Community Activities").ownerEmail("travel@manacommunity.app")
                .deployedServiceName("mana-community-travel").defaultPort(8097).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_travel_db").type("PostgreSQL")
                        .primaryEntities(List.of("trips", "carpool_routes", "shuttle_schedules", "boarding_tickets")).build())
                .internalDependencies(List.of("payment-service", "notification-service"))
                .externalDependencies(List.of("Google Maps API", "Redis", "Kafka"))
                .gatewayRouteIds(List.of("travel-service")).pathPatterns(List.of("/api/v1/travel/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("helpdesk-service").displayName("Helpdesk and Maintenance Service")
                .owningTeam("Facilities Management").ownerEmail("helpdesk@manacommunity.app")
                .deployedServiceName("mana-community-helpdesk").defaultPort(8098).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_helpdesk_db").type("PostgreSQL")
                        .primaryEntities(List.of("tickets", "technicians", "sla_policies", "escalation_rules")).build())
                .internalDependencies(List.of("vendor-service", "notification-service", "media-service"))
                .externalDependencies(List.of("Redis", "Kafka"))
                .gatewayRouteIds(List.of("helpdesk-service")).pathPatterns(List.of("/api/v1/helpdesk/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        addInventory(ServiceInventoryEntry.builder()
                .serviceId("graph-service").displayName("Community Graph and Recommendations Service")
                .owningTeam("Community Social").ownerEmail("graph-team@manacommunity.app")
                .deployedServiceName("mana-community-graph").defaultPort(8099).apiVersions(List.of("v1"))
                .databaseOwnership(DatabaseOwnership.builder().databaseName("mana_graph_db").type("PostgreSQL")
                        .primaryEntities(List.of("community_nodes", "relationship_edges", "interest_groups", "recommendations")).build())
                .internalDependencies(List.of("social-service", "professional-network-service"))
                .externalDependencies(List.of("Neo4j", "Redis", "Kafka"))
                .gatewayRouteIds(List.of("graph-service")).pathPatterns(List.of("/api/v1/graph/**"))
                .healthEndpoint("/actuator/health").active(true).build());

        log.info("Initialized ServiceInventory with {} platform service entries", inventory.size());
    }

    // -------------------------------------------------------------------------
    // Route Registration
    // -------------------------------------------------------------------------

    public void register(RegisteredRoute route) {
        registry.put(route.getRouteId(), route);
    }

    public void addInventory(ServiceInventoryEntry entry) {
        inventory.put(entry.getServiceId(), entry);
    }

    // -------------------------------------------------------------------------
    // Route Queries
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Service Inventory Queries
    // -------------------------------------------------------------------------

    /** Returns the full machine-readable service inventory. */
    public Map<String, ServiceInventoryEntry> getMachineReadableInventory() {
        return Collections.unmodifiableMap(inventory);
    }

    /** Returns a dependency graph: serviceId -> list of internal dependency serviceIds. */
    public Map<String, List<String>> getDependencyGraph() {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        inventory.forEach((id, entry) -> graph.put(id, entry.getInternalDependencies()));
        return Collections.unmodifiableMap(graph);
    }

    /** Returns a map of serviceId -> DatabaseOwnership for all registered services. */
    public Map<String, DatabaseOwnership> getDatabaseOwnershipMap() {
        Map<String, DatabaseOwnership> dbMap = new LinkedHashMap<>();
        inventory.forEach((id, entry) -> dbMap.put(id, entry.getDatabaseOwnership()));
        return Collections.unmodifiableMap(dbMap);
    }

    // -------------------------------------------------------------------------
    // Anomaly Detection
    // -------------------------------------------------------------------------

    /**
     * Detects duplicated path patterns across registered routes and port collisions
     * across service inventory entries.
     */
    public List<DuplicateEndpoint> detectDuplicatedEndpoints() {
        List<DuplicateEndpoint> duplicates = new ArrayList<>();

        // 1. Exact path pattern duplicates across registry routes
        Map<String, List<String>> patternToRoutes = new LinkedHashMap<>();
        registry.values().forEach(route -> {
            String pattern = route.getPathPattern();
            patternToRoutes.computeIfAbsent(pattern, k -> new ArrayList<>()).add(route.getRouteId());
        });
        patternToRoutes.forEach((pattern, routeIds) -> {
            if (routeIds.size() > 1) {
                duplicates.add(DuplicateEndpoint.builder()
                        .pathPattern(pattern)
                        .conflictingRouteIds(routeIds)
                        .conflictType("EXACT_DUPLICATE")
                        .build());
            }
        });

        // 2. Port collisions across inventory entries
        Map<Integer, List<String>> portToServices = new LinkedHashMap<>();
        inventory.values().forEach(entry ->
            portToServices.computeIfAbsent(entry.getDefaultPort(), k -> new ArrayList<>()).add(entry.getServiceId())
        );
        portToServices.forEach((port, serviceIds) -> {
            if (serviceIds.size() > 1) {
                duplicates.add(DuplicateEndpoint.builder()
                        .pathPattern("PORT:" + port)
                        .conflictingRouteIds(serviceIds)
                        .conflictType("PORT_COLLISION")
                        .build());
            }
        });

        return duplicates;
    }

    /**
     * Detects gateway routes (known from application.yml) that are not backed by
     * any registered service inventory entry.
     */
    public List<OrphanedRoute> detectOrphanedRoutes() {
        List<OrphanedRoute> orphans = new ArrayList<>();

        // Routes present in the gateway YAML but not in the registry
        for (String gatewayRouteId : GATEWAY_ONLY_ROUTE_IDS) {
            if (!registry.containsKey(gatewayRouteId) && !inventory.containsKey(gatewayRouteId)) {
                orphans.add(OrphanedRoute.builder()
                        .routeId(gatewayRouteId)
                        .pathPattern("unknown - see application.yml")
                        .reason("NO_REGISTERED_BACKEND")
                        .build());
            }
        }

        // Inventory entries marked inactive but still route-registered
        inventory.values().stream()
                .filter(e -> !e.isActive() && registry.containsKey(e.getServiceId()))
                .forEach(e -> orphans.add(OrphanedRoute.builder()
                        .routeId(e.getServiceId())
                        .pathPattern(registry.get(e.getServiceId()).getPathPattern())
                        .reason("INACTIVE_SERVICE")
                        .build()));

        return orphans;
    }

    /**
     * Generates a combined anomaly report covering duplicates and orphans.
     */
    public RegistryAnomalyReport generateAnomalyReport() {
        List<DuplicateEndpoint> duplicates = detectDuplicatedEndpoints();
        List<OrphanedRoute> orphans = detectOrphanedRoutes();
        int total = duplicates.size() + orphans.size();
        String health = total == 0 ? "HEALTHY" : (total <= 3 ? "WARNING" : "CRITICAL");
        return RegistryAnomalyReport.builder()
                .duplicatedEndpoints(duplicates)
                .orphanedRoutes(orphans)
                .totalAnomalies(total)
                .overallHealth(health)
                .generatedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // Health Probes
    // -------------------------------------------------------------------------

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