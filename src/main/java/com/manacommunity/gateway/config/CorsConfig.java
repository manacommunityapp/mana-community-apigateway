package com.manacommunity.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Programmatic CORS configuration serving as a fallback.
 * Primary CORS settings are configured via application.yml
 * (spring.cloud.gateway.globalcors).
 */
@Configuration
public class CorsConfig {

    @Value("${gateway.cors.allowed-origins:https://app.manacommunity.com,https://admin.manacommunity.com,http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        if ("*".equals(allowedOrigins.trim())) {
            // OWASP/Browser rule: Never combine wildcard origin with allowCredentials: true
            corsConfiguration.addAllowedOrigin("*");
            corsConfiguration.setAllowCredentials(false);
        } else {
            List<String> origins = List.of(allowedOrigins.split(","));
            origins.forEach(origin -> {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    corsConfiguration.addAllowedOrigin(trimmed);
                }
            });
            corsConfiguration.setAllowCredentials(true);
        }

        corsConfiguration.addAllowedMethod("GET");
        corsConfiguration.addAllowedMethod("POST");
        corsConfiguration.addAllowedMethod("PUT");
        corsConfiguration.addAllowedMethod("DELETE");
        corsConfiguration.addAllowedMethod("PATCH");
        corsConfiguration.addAllowedMethod("OPTIONS");
        corsConfiguration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Community-Id",
                "X-Correlation-Id",
                "Idempotency-Key"
        ));
        corsConfiguration.setExposedHeaders(List.of(
                "Authorization",
                "X-Correlation-Id",
                "Idempotency-Key"
        ));
        corsConfiguration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);

        return new CorsWebFilter(source);
    }
}
