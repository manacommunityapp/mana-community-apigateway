package com.manacommunity.gateway.tracing;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class TracingConfig {

    @Value("${spring.application.name:mana-api-gateway}")
    private String serviceName;

    @Value("${spring.profiles.active:default}")
    private String environment;

    @Bean
    public ObservationRegistryCustomizer<ObservationRegistry> observationRegistryCustomizer() {
        return registry -> {
            log.info("Configuring observation registry for service: {}, environment: {}",
                    serviceName, environment);
        };
    }
}
