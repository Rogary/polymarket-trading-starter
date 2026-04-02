package com.gary.polymarket.trading.exception;

public class PolymarketSigningException extends PolymarketTradingException {

    public PolymarketSigningException(String message) {
        super("SIGNING_ERROR", message, null);
    }

    public PolymarketSigningException(String message, Throwable cause) {
        super("SIGNING_ERROR", message, null, cause);
    }
}
