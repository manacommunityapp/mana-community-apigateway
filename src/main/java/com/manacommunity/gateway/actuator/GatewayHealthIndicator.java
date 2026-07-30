package com.manacommunity.gateway.actuator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GatewayHealthIndicator implements ReactiveHealthIndicator {

    private final ReactiveStringRedisTemplate redisTemplate;

    public GatewayHealthIndicator(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Health> health() {
        return checkRedisHealth()
                .map(redisUp -> {
                    Health.Builder builder = redisUp ? Health.up() : Health.down();
                    builder.withDetail("redis", redisUp ? "connected" : "disconnected");
                    return builder.build();
                })
                .onErrorResume(ex -> {
                    log.error("Health check failed: {}", ex.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("redis", "disconnected")
                            .withDetail("error", ex.getMessage())
                            .build());
                });
    }

    private Mono<Boolean> checkRedisHealth() {
        return redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .ping()
                .map("PONG"::equals)
                .onErrorReturn(false);
    }
}
