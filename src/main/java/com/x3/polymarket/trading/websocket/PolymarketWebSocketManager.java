package com.x3.polymarket.trading.websocket;

import com.x3.polymarket.trading.PolymarketTradingProperties;
import com.x3.polymarket.trading.auth.L1Authenticator;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages both market and user WebSocket channel clients.
 * Implements {@link SmartLifecycle} for automatic start/stop with the Spring context.
 */
public class PolymarketWebSocketManager implements SmartLifecycle {

    private final MarketChannelClient marketClient;
    private final UserChannelClient userClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PolymarketWebSocketManager(PolymarketTradingProperties properties,
                                      L1Authenticator l1Authenticator,
                                      List<WebSocketEventListener> listeners) {
        this.marketClient = new MarketChannelClient(properties, listeners);
        this.userClient = new UserChannelClient(properties, l1Authenticator, listeners);
    }

    // ── Public API ─────────────────────────────────────────────────────

    public void subscribeMarket(List<String> assetIds) {
        marketClient.subscribe(assetIds);
    }

    public void unsubscribeMarket(List<String> assetIds) {
        marketClient.unsubscribe(assetIds);
    }

    public void subscribeUser(List<String> marketIds) {
        userClient.subscribe(marketIds);
    }

    public MarketChannelClient getMarketClient() {
        return marketClient;
    }

    public UserChannelClient getUserClient() {
        return userClient;
    }

    // ── SmartLifecycle ─────────────────────────────────────────────────

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            marketClient.connect();
            userClient.connect();
        }
    }

    @Override
    public void stop() {
        shutdown();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 2;
    }

    /**
     * Shuts down both channel clients and releases resources.
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            marketClient.shutdown();
            userClient.shutdown();
        }
    }
}
