package com.manacommunity.gateway.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.tenant")
public class TenantProperties {

    private String headerName;
    private String defaultTenant;
    private List<String> supportedTypes;
}
