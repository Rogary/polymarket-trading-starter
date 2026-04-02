package com.gary.polymarket.trading.exception;

import lombok.Getter;

@Getter
public class PolymarketTradingException extends RuntimeException {

    private final String errorCode;
    private final String rawResponse;

    public PolymarketTradingException(String message) {
        super(message);
        this.errorCode = null;
        this.rawResponse = null;
    }

    public PolymarketTradingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.rawResponse = null;
    }

    public PolymarketTradingException(String errorCode, String message, String rawResponse) {
        super(message);
        this.errorCode = errorCode;
        this.rawResponse = rawResponse;
    }

    public PolymarketTradingException(String errorCode, String message, String rawResponse, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.rawResponse = rawResponse;
    }
}
