package com.manacommunity.gateway.filters;

import com.manacommunity.gateway.constants.GatewayConstants;
import com.manacommunity.gateway.constants.HeaderConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    @Test
    void shouldGenerateCorrelationIdWhenNotPresent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(HeaderConstants.X_CORRELATION_ID);

        assertThat(correlationId).isNull();
    }

    @Test
    void shouldPreserveExistingCorrelationId() {
        String existingId = "existing-correlation-id";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header(HeaderConstants.X_CORRELATION_ID, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(HeaderConstants.X_CORRELATION_ID);

        assertThat(correlationId).isEqualTo(existingId);
    }

    @Test
    void exchangeAttributeKeys_areDefined() {
        assertThat(GatewayConstants.CORRELATION_ID_ATTR).isEqualTo("correlationId");
        assertThat(GatewayConstants.REQUEST_ID_ATTR).isEqualTo("requestId");
    }
}
