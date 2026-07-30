package com.manacommunity.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Slf4j
@Configuration
public class CircuitBreakerConfig {

    private static final List<String> CIRCUIT_BREAKER_IDS = List.of(
            "default", "identity", "finance", "ai"
    );

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> circuitBreakerCustomizer(
            CircuitBreakerRegistry circuitBreakerRegistry) {

        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                            .failureRateThreshold(50)
                            .waitDurationInOpenState(Duration.ofSeconds(30))
                            .slidingWindowSize(10)
                            .minimumNumberOfCalls(5)
                            .permittedNumberOfCallsInHalfOpenState(3)
                            .automaticTransitionFromOpenToHalfOpenEnabled(true)
                            .build())
                    .timeLimiterConfig(TimeLimiterConfig.custom()
                            .timeoutDuration(Duration.ofSeconds(10))
                            .build())
                    .build());

            registerEventListeners(circuitBreakerRegistry);
        };
    }

    private void registerEventListeners(CircuitBreakerRegistry registry) {
        CIRCUIT_BREAKER_IDS.forEach(id -> {
            try {
                io.github.resilience4j.circuitbreaker.CircuitBreaker cb = registry.circuitBreaker(id);
                cb.getEventPublisher()
                        .onStateTransition(event ->
                                log.warn("Circuit breaker '{}' state transition: {} -> {}",
                                        event.getCircuitBreakerName(),
                                        event.getStateTransition().getFromState(),
                                        event.getStateTransition().getToState()))
                        .onFailureRateExceeded(event ->
                                log.warn("Circuit breaker '{}' failure rate exceeded: {}%",
                                        event.getCircuitBreakerName(),
                                        event.getFailureRate()))
                        .onSlowCallRateExceeded(event ->
                                log.warn("Circuit breaker '{}' slow call rate exceeded: {}%",
                                        event.getCircuitBreakerName(),
                                        event.getSlowCallRate()));
            } catch (Exception e) {
                log.debug("Circuit breaker '{}' not yet registered, event listeners will be attached on first use", id);
            }
        });
    }
}
