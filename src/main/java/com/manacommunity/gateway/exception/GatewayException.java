package com.manacommunity.gateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GatewayException extends RuntimeException {

    private final int status;
    private final String error;

    public GatewayException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public GatewayException(HttpStatus status, String message) {
        super(message);
        this.status = status.value();
        this.error = status.getReasonPhrase();
    }
}
