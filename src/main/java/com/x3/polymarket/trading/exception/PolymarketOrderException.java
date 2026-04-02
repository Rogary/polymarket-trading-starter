package com.x3.polymarket.trading.exception;

public class PolymarketOrderException extends PolymarketTradingException {

    public PolymarketOrderException(String message, String rawResponse) {
        super("ORDER_ERROR", message, rawResponse);
    }
}
