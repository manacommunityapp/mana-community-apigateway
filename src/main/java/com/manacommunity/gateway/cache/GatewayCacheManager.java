package com.manacommunity.gateway.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class GatewayCacheManager {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final CacheManager caffeineCacheManager;

    public GatewayCacheManager(ReactiveRedisTemplate<String, String> reactiveRedisTemplate,
                               CacheManager caffeineCacheManager) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.caffeineCacheManager = caffeineCacheManager;
    }

    public Mono<String> getCachedValue(String cacheName, String key) {
        // Check Caffeine (L1 cache) first
        Cache caffeineCache = caffeineCacheManager.getCache(cacheName);
        if (caffeineCache != null) {
            Cache.ValueWrapper wrapper = caffeineCache.get(key);
            if (wrapper != null && wrapper.get() != null) {
                log.debug("L1 cache hit [cacheName={}, key={}]", cacheName, key);
                return Mono.just((String) wrapper.get());
            }
        }

        // Fall back to Redis (L2 cache)
        String redisKey = buildRedisKey(cacheName, key);
        return reactiveRedisTemplate.opsForValue()
                .get(redisKey)
                .doOnNext(value -> {
                    log.debug("L2 cache hit [cacheName={}, key={}]", cacheName, key);
                    // Populate L1 cache from L2
                    if (caffeineCache != null) {
                        caffeineCache.put(key, value);
                    }
                })
                .doOnSubscribe(s -> log.debug("L1 cache miss, checking L2 [cacheName={}, key={}]",
                        cacheName, key))
                .onErrorResume(ex -> {
                    log.warn("Redis cache lookup failed [cacheName={}, key={}]: {}",
                            cacheName, key, ex.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> putCachedValue(String cacheName, String key, String value, Duration ttl) {
        // Write to Caffeine (L1)
        Cache caffeineCache = caffeineCacheManager.getCache(cacheName);
        if (caffeineCache != null) {
            caffeineCache.put(key, value);
        }

        // Write to Redis (L2)
        String redisKey = buildRedisKey(cacheName, key);
        return reactiveRedisTemplate.opsForValue()
                .set(redisKey, value, ttl)
                .doOnSuccess(success ->
                        log.debug("Cached value written [cacheName={}, key={}, ttl={}]",
                                cacheName, key, ttl))
                .onErrorResume(ex -> {
                    log.warn("Failed to write to Redis cache [cacheName={}, key={}]: {}",
                            cacheName, key, ex.getMessage());
                    return Mono.just(false);
                })
                .then();
    }

    public Mono<Void> evictCache(String cacheName, String key) {
        // Evict from Caffeine (L1)
        Cache caffeineCache = caffeineCacheManager.getCache(cacheName);
        if (caffeineCache != null) {
            caffeineCache.evict(key);
        }

        // Evict from Redis (L2)
        String redisKey = buildRedisKey(cacheName, key);
        return reactiveRedisTemplate.delete(redisKey)
                .doOnSuccess(count ->
                        log.debug("Cache evicted [cacheName={}, key={}, redisKeysRemoved={}]",
                                cacheName, key, count))
                .onErrorResume(ex -> {
                    log.warn("Failed to evict from Redis cache [cacheName={}, key={}]: {}",
                            cacheName, key, ex.getMessage());
                    return Mono.just(0L);
                })
                .then();
    }

    private String buildRedisKey(String cacheName, String key) {
        return cacheName + ":" + key;
    }
}
