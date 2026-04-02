package com.gary.polymarket.trading.exception;

public class PolymarketAuthException extends PolymarketTradingException {

    public PolymarketAuthException(String message) {
        super("AUTH_ERROR", message, null);
    }

    public PolymarketAuthException(String message, Throwable cause) {
        super("AUTH_ERROR", message, null, cause);
    }
}
