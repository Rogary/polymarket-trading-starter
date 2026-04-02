package com.gary.polymarket.trading.exception;

public class PolymarketWebSocketException extends PolymarketTradingException {

    public PolymarketWebSocketException(String message) {
        super("WEBSOCKET_ERROR", message, null);
    }

    public PolymarketWebSocketException(String message, Throwable cause) {
        super("WEBSOCKET_ERROR", message, null, cause);
    }
}
