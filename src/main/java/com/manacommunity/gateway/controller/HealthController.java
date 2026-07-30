package com.manacommunity.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/gateway")
public class HealthController {

    private final Environment environment;

    @Value("${spring.application.name:mana-api-gateway}")
    private String applicationName;

    @Value("${app.version:1.0.0}")
    private String version;

    public HealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        Map<String, Object> healthInfo = new LinkedHashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("timestamp", Instant.now().toString());
        healthInfo.put("version", version);
        return Mono.just(healthInfo);
    }

    @GetMapping("/info")
    public Mono<Map<String, Object>> info() {
        Map<String, Object> gatewayInfo = new LinkedHashMap<>();
        gatewayInfo.put("name", applicationName);
        gatewayInfo.put("version", version);
        gatewayInfo.put("profiles", environment.getActiveProfiles());
        return Mono.just(gatewayInfo);
    }
}
