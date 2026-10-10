package com.manacommunity.gateway.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RouteRegistryTest {

    private RouteRegistry routeRegistry;

    @BeforeEach
    void setUp() {
        routeRegistry = new RouteRegistry();
    }

    @Test
    void testRegistryContainsAllCoreAndPlatformServices() {
        List<RouteRegistry.RegisteredRoute> routes = routeRegistry.getAllRoutes();
        assertTrue(routes.size() >= 20, "Should have at least 20 platform services registered, found: " + routes.size());

        assertTrue(routeRegistry.findByOwningService("Auth").isPresent());
        assertTrue(routeRegistry.findByOwningService("Sports").isPresent());
        assertTrue(routeRegistry.findByOwningService("Events").isPresent());
        assertTrue(routeRegistry.findByOwningService("Finance").isPresent());
        assertTrue(routeRegistry.findByOwningService("Payment").isPresent());
        assertTrue(routeRegistry.findByOwningService("Marketplace").isPresent());
        assertTrue(routeRegistry.findByOwningService("Notification").isPresent());
        assertTrue(routeRegistry.findByOwningService("Food").isPresent());
        assertTrue(routeRegistry.findByOwningService("Media").isPresent());
        assertTrue(routeRegistry.findByOwningService("Resource Booking").isPresent());
        assertTrue(routeRegistry.findByOwningService("Social").isPresent());
        assertTrue(routeRegistry.findByOwningService("Wallet").isPresent());
        assertTrue(routeRegistry.findByOwningService("Emergency").isPresent());
    }

    @Test
    void testAuthServiceVerificationContract() {
        Optional<RouteRegistry.RegisteredRoute> auth = routeRegistry.findByOwningService("Auth");
        assertTrue(auth.isPresent());
        assertEquals("mana-community-auth", auth.get().getDeployedServiceName());
        assertEquals(8083, auth.get().getDefaultPort());
        assertEquals("Issuer, token, refresh and logout", auth.get().getRequiredVerification());
        assertTrue(auth.get().getSupportedControllerPaths().contains("/api/auth/login"));
        assertTrue(auth.get().getSupportedControllerPaths().contains("/api/auth/refresh"));
        assertTrue(auth.get().getSupportedControllerPaths().contains("/api/auth/logout"));
    }

    @Test
    void testSportsServiceVerificationContract() {
        Optional<RouteRegistry.RegisteredRoute> sports = routeRegistry.findByOwningService("Sports");
        assertTrue(sports.isPresent());
        assertEquals("mana-community-sports", sports.get().getDeployedServiceName());
        assertEquals(8082, sports.get().getDefaultPort());
        assertEquals("Tournament, auction and scoring", sports.get().getRequiredVerification());
        assertTrue(sports.get().getSupportedControllerPaths().contains("/api/tournaments/**"));
        assertTrue(sports.get().getSupportedControllerPaths().contains("/api/auction/live"));
    }

    @Test
    void testEventsServiceVerificationContract() {
        Optional<RouteRegistry.RegisteredRoute> events = routeRegistry.findByOwningService("Events");
        assertTrue(events.isPresent());
        assertEquals("mana-community-events", events.get().getDeployedServiceName());
        assertEquals(8096, events.get().getDefaultPort());
        assertEquals("Registration, permissions and notifications", events.get().getRequiredVerification());
        assertTrue(events.get().getSupportedControllerPaths().contains("/api/events/registrations"));
    }

    @Test
    void testFinanceServiceVerificationContract() {
        Optional<RouteRegistry.RegisteredRoute> finance = routeRegistry.findByOwningService("Finance");
        assertTrue(finance.isPresent());
        assertEquals("mana-community-finance", finance.get().getDeployedServiceName());
        assertEquals(8086, finance.get().getDefaultPort());
        assertEquals("Tenant isolation and financial integrity", finance.get().getRequiredVerification());
        assertTrue(finance.get().getSupportedControllerPaths().contains("/api/v1/finance/ledger"));
        assertTrue(finance.get().getSupportedControllerPaths().contains("/api/finance/invoices"));
    }

    @Test
    void testPaymentServiceVerificationContract() {
        Optional<RouteRegistry.RegisteredRoute> payment = routeRegistry.findByOwningService("Payment");
        assertTrue(payment.isPresent());
        assertEquals("mana-community-payment", payment.get().getDeployedServiceName());
        assertEquals(8087, payment.get().getDefaultPort());
        assertEquals("Signed webhooks and idempotency", payment.get().getRequiredVerification());
        assertTrue(payment.get().getSupportedControllerPaths().contains("/api/payments/webhooks"));
        assertTrue(payment.get().getSupportedControllerPaths().contains("/api/payments/intents"));
    }

    @Test
    void testMarketplaceServiceVerificationContract() {
        Optional<RouteRegistry.RegisteredRoute> marketplace = routeRegistry.findByOwningService("Marketplace");
        assertTrue(marketplace.isPresent());
        assertEquals("mana-community-marketplace", marketplace.get().getDeployedServiceName());
        assertEquals(8085, marketplace.get().getDefaultPort());
        assertEquals("Orders, payments and refunds", marketplace.get().getRequiredVerification());
        assertTrue(marketplace.get().getSupportedControllerPaths().contains("/api/v1/marketplace/orders"));
    }

    @Test
    void testNotificationServiceVerificationContract() {
        Optional<RouteRegistry.RegisteredRoute> notification = routeRegistry.findByOwningService("Notification");
        assertTrue(notification.isPresent());
        assertEquals("mana-community-notification", notification.get().getDeployedServiceName());
        assertEquals(8093, notification.get().getDefaultPort());
        assertEquals("Event delivery, retries and preferences", notification.get().getRequiredVerification());
        assertTrue(notification.get().getSupportedControllerPaths().contains("/api/notifications"));
        assertTrue(notification.get().getSupportedControllerPaths().contains("/api/notifications/dlq"));
    }

    @Test
    void testPathMatching() {
        Optional<RouteRegistry.RegisteredRoute> route = routeRegistry.findMatchingRoute("/api/v1/auth/login");
        assertTrue(route.isPresent());
        assertEquals("Auth", route.get().getOwningService());

        Optional<RouteRegistry.RegisteredRoute> paymentRoute = routeRegistry.findMatchingRoute("/api/payments/intents");
        assertTrue(paymentRoute.isPresent());
        assertEquals("Payment", paymentRoute.get().getOwningService());
    }

    @Test
    void testDownstreamServiceHealthProbeReturnsStatus() {
        Optional<RouteRegistry.RegisteredRoute> auth = routeRegistry.findByOwningService("Auth");
        assertTrue(auth.isPresent());
        RouteRegistry.ServiceHealthStatus status = routeRegistry.checkServiceHealth(auth.get()).block();
        assertNotNull(status);
        assertEquals("Auth", status.getServiceName());
        assertTrue(status.getStatus().equals("UP") || status.getStatus().equals("DOWN"));
    }

    @Test
    void testCheckAllServicesSummary() {
        Map<String, Object> summary = routeRegistry.checkAllServices().block();
        assertNotNull(summary);
        assertTrue(summary.containsKey("status"));
        assertTrue(summary.containsKey("totalServices"));
        assertEquals(20, summary.get("totalServices"));
    }
}