package com.manacommunity.gateway.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.jwt")
public class JwtProperties {

    private String secret;
    private long expiration;
    private long refreshExpiration;
    private String issuer;
}
