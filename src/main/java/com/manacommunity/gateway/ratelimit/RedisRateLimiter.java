package com.manacommunity.gateway.ratelimit;

import com.manacommunity.gateway.dto.RateLimitInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class RedisRateLimiter {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RateLimitConfig rateLimitConfig;

    public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate, RateLimitConfig rateLimitConfig) {
        this.redisTemplate = redisTemplate;
        this.rateLimitConfig = rateLimitConfig;
    }

    public Mono<Boolean> isAllowed(String key, int maxRequests, int durationSeconds) {
        if (!rateLimitConfig.isEnabled()) {
            return Mono.just(true);
        }

        String redisKey = buildKey(key);
        Duration ttl = Duration.ofSeconds(durationSeconds);

        return redisTemplate.opsForValue()
                .increment(redisKey)
                .flatMap(currentCount -> {
                    if (currentCount == 1L) {
                        return redisTemplate.expire(redisKey, ttl)
                                .thenReturn(true);
                    }
                    if (currentCount > maxRequests) {
                        log.warn("Rate limit exceeded for key: {}, count: {}, limit: {}",
                                key, currentCount, maxRequests);
                        return Mono.just(false);
                    }
                    return Mono.just(true);
                })
                .onErrorResume(ex -> {
                    log.error("Rate limit check failed for key: {}, allowing request. Error: {}",
                            key, ex.getMessage());
                    return Mono.just(true);
                });
    }

    public Mono<RateLimitInfo> getRateLimitInfo(String key, int maxRequests, int durationSeconds) {
        String redisKey = buildKey(key);

        return redisTemplate.opsForValue()
                .get(redisKey)
                .defaultIfEmpty("0")
                .zipWith(redisTemplate.getExpire(redisKey).defaultIfEmpty(Duration.ZERO))
                .map(tuple -> {
                    long currentCount = Long.parseLong(tuple.getT1());
                    Duration remainingTtl = tuple.getT2();
                    long remaining = Math.max(0, maxRequests - currentCount);
                    long resetTimestamp = Instant.now()
                            .plusSeconds(remainingTtl.getSeconds())
                            .getEpochSecond();

                    return RateLimitInfo.builder()
                            .remaining(remaining)
                            .limit(maxRequests)
                            .resetTimestamp(resetTimestamp)
                            .build();
                })
                .onErrorResume(ex -> {
                    log.error("Failed to get rate limit info for key: {}. Error: {}",
                            key, ex.getMessage());
                    return Mono.just(RateLimitInfo.builder()
                            .remaining(maxRequests)
                            .limit(maxRequests)
                            .resetTimestamp(Instant.now()
                                    .plusSeconds(durationSeconds)
                                    .getEpochSecond())
                            .build());
                });
    }

    public static String formatKey(String tenantId, String clientIp, String path) {
        return String.join(":", tenantId, clientIp, path);
    }

    private String buildKey(String key) {
        return RATE_LIMIT_KEY_PREFIX + key;
    }
}
