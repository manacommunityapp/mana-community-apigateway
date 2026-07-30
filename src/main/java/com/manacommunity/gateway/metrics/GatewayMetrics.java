package com.manacommunity.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class GatewayMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeConnections;

    public GatewayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.activeConnections = new AtomicInteger(0);
        meterRegistry.gauge("gateway.connections.active", activeConnections);
    }

    public void recordRequestDuration(String route, String method, int status, long durationMs) {
        Timer.builder("gateway.request.duration")
                .tag("route", route)
                .tag("method", method)
                .tag("status", String.valueOf(status))
                .description("Duration of gateway requests")
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    public void incrementRequestCount(String route, String method) {
        Counter.builder("gateway.request.count")
                .tag("route", route)
                .tag("method", method)
                .description("Total number of gateway requests")
                .register(meterRegistry)
                .increment();
    }

    public void incrementErrorCount(String route, int status) {
        Counter.builder("gateway.request.errors")
                .tag("route", route)
                .tag("status", String.valueOf(status))
                .description("Total number of gateway errors")
                .register(meterRegistry)
                .increment();
    }

    public void recordActiveConnections(int count) {
        activeConnections.set(count);
    }

    public void recordRateLimitRejection(String route) {
        Counter.builder("gateway.rate_limit.rejected")
                .tag("route", route)
                .description("Total number of requests rejected by rate limiting")
                .register(meterRegistry)
                .increment();
    }
}
