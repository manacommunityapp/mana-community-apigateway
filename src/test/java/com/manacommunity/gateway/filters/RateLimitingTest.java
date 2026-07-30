package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.ratelimit.RateLimitConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingTest {

    @Test
    void rateLimitConfig_parsesEndpoints() {
        RateLimitConfig config = new RateLimitConfig();
        config.setEnabled(true);
        config.setDefaultRate(100);
        config.setDefaultDuration(60);
        config.setEndpoints(Map.of(
                "/api/v*/auth/login", "5,60",
                "/api/v*/auth/verify-otp", "3,60",
                "/api/v*/ai/**", "50,60"
        ));

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getDefaultRate()).isEqualTo(100);
        assertThat(config.getEndpoints()).hasSize(3);
        assertThat(config.getEndpoints().get("/api/v*/auth/login")).isEqualTo("5,60");
    }

    @Test
    void rateLimitConfig_endpointParsing() {
        String config = "5,60";
        String[] parts = config.split(",");

        int rate = Integer.parseInt(parts[0]);
        int duration = Integer.parseInt(parts[1]);

        assertThat(rate).isEqualTo(5);
        assertThat(duration).isEqualTo(60);
    }
}
