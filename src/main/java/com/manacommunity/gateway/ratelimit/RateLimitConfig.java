package com.manacommunity.gateway.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitConfig {

    private boolean enabled = true;
    private int defaultRate = 100;
    private int defaultDuration = 60;

    /**
     * Endpoint-specific rate limit configuration.
     * Key: path pattern (e.g., "/api/identity/**")
     * Value: "rate,durationSeconds" format (e.g., "50,60")
     */
    private Map<String, String> endpoints = new HashMap<>();

    public int[] parseEndpointConfig(String path) {
        String config = endpoints.get(path);
        if (config == null || config.isBlank()) {
            return new int[]{defaultRate, defaultDuration};
        }

        String[] parts = config.split(",");
        if (parts.length != 2) {
            return new int[]{defaultRate, defaultDuration};
        }

        try {
            int rate = Integer.parseInt(parts[0].trim());
            int duration = Integer.parseInt(parts[1].trim());
            return new int[]{rate, duration};
        } catch (NumberFormatException e) {
            return new int[]{defaultRate, defaultDuration};
        }
    }
}
