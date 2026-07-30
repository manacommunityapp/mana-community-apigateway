package com.manacommunity.gateway.config;

import com.manacommunity.gateway.security.JwtProperties;
import com.manacommunity.gateway.tenant.TenantProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, TenantProperties.class})
public class GatewayConfig {

}
