package com.gary.polymarket.trading.exception;

public class PolymarketRateLimitException extends PolymarketTradingException {

    public PolymarketRateLimitException(String message, String rawResponse) {
        super("RATE_LIMIT", message, rawResponse);
    }
}
