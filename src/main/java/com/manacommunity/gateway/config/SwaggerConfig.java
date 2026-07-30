package com.manacommunity.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi gatewayApi() {
        return GroupedOpenApi.builder()
                .group("gateway")
                .pathsToMatch("/gateway/**", "/fallback/**")
                .build();
    }

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mana Community API Gateway")
                        .version("1.0")
                        .description("API Gateway for the Mana Community platform. "
                                + "Routes and manages requests to downstream microservices "
                                + "with authentication, rate limiting, circuit breaking, and observability.")
                        .contact(new Contact()
                                .name("Mana Community")
                                .email("support@manacommunity.com")));
    }
}
