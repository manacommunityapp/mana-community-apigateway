package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.HeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Extracts the Accept-Language header from the incoming request, normalizes it
 * to a standard locale tag (defaulting to "en"), stores it as an exchange
 * attribute, and forwards it to downstream services.
 */
@Slf4j
@Component
public class LocalizationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = 0;
    private static final String LOCALE_ATTR = "locale";
    private static final String DEFAULT_LANGUAGE = "en";

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String acceptLanguage = exchange.getRequest().getHeaders().getFirst(HeaderConstants.ACCEPT_LANGUAGE);

        String language;
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            // Parse the primary language from Accept-Language (e.g., "en-US,en;q=0.9" -> "en")
            language = Locale.LanguageRange.parse(acceptLanguage).stream()
                    .findFirst()
                    .map(range -> {
                        String tag = range.getRange();
                        // Handle wildcard
                        return "*".equals(tag) ? DEFAULT_LANGUAGE : tag;
                    })
                    .orElse(DEFAULT_LANGUAGE);
        } else {
            language = DEFAULT_LANGUAGE;
        }

        // Store locale in exchange attributes
        exchange.getAttributes().put(LOCALE_ATTR, language);

        log.debug("Resolved locale: {} for path: {}", language, exchange.getRequest().getURI().getPath());

        // Ensure Accept-Language header is forwarded downstream
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HeaderConstants.ACCEPT_LANGUAGE, language)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return chain.filter(mutatedExchange);
    }
}
