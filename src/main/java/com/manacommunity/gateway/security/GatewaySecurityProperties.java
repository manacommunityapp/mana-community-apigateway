package com.manacommunity.gateway.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds public paths configuration with safe defaults for Swagger, Actuator,
 * Fallback endpoints, Route Registry, and public Authentication endpoints.
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    private List<String> publicPaths = new ArrayList<>(List.of(
            "/api/v*/auth/**",
            "/api/auth/**",
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/fallback/**",
            "/gateway/**"
    ));
}