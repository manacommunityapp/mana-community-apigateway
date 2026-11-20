package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.HeaderConstants;
import com.manacommunity.gateway.ratelimit.RateLimitConfig;
import com.manacommunity.gateway.ratelimit.RedisRateLimiter;
import com.manacommunity.gateway.security.GatewaySecurityProperties;
import com.manacommunity.gateway.tenant.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private TenantResolver tenantResolver;

    @Mock
    private GatewayFilterChain chain;

    private RateLimitConfig rateLimitConfig;
    private GatewaySecurityProperties securityProperties;
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig();
        rateLimitConfig.setEnabled(true);
        rateLimitConfig.setDefaultRate(100);
        rateLimitConfig.setDefaultDuration(60);
        rateLimitConfig.setEndpoints(Map.of(
                "/api/v*/auth/login", "5,60"
        ));

        securityProperties = new GatewaySecurityProperties();
        rateLimitFilter = new RateLimitFilter(redisRateLimiter, rateLimitConfig, tenantResolver, securityProperties);
    }

    @Test
    void filter_whenDisabled_shouldProceedDirectly() {
        rateLimitConfig.setEnabled(false);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisRateLimiter);
    }

    @Test
    void filter_whenExcludedPath_shouldBypassLimiter() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisRateLimiter);
    }

    @Test
    void filter_whenAllowed_shouldSetLimitHeaderAndProceed() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(tenantResolver.resolveTenantId(exchange)).thenReturn("tenant-1");
        when(redisRateLimiter.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(Mono.just(true));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_RATE_LIMIT_LIMIT)).isEqualTo("5");
    }

    @Test
    void filter_whenLimitExceeded_shouldReturn429WithHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(tenantResolver.resolveTenantId(exchange)).thenReturn("tenant-1");
        when(redisRateLimiter.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(Mono.just(false));

        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(chain);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_RATE_LIMIT_LIMIT)).isEqualTo("5");
        assertThat(exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_RATE_LIMIT_REMAINING)).isEqualTo("0");
        assertThat(exchange.getResponse().getHeaders().getFirst(HeaderConstants.X_RATE_LIMIT_RESET)).isNotNull();
    }
}
