package com.gary.polymarket.trading.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gary.polymarket.trading.PolymarketTradingProperties;
import com.gary.polymarket.trading.enums.ChannelType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket client for the Polymarket market channel.
 * Handles order-book snapshots, price changes, and market lifecycle events.
 */
public class MarketChannelClient extends WebSocketListener {

    private static final Logger log = LoggerFactory.getLogger(MarketChannelClient.class);

    private final PolymarketTradingProperties properties;
    private final List<WebSocketEventListener> listeners;
    private final CopyOnWriteArraySet<String> subscribedAssetIds = new CopyOnWriteArraySet<>();
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    private volatile WebSocket webSocket;
    private volatile ScheduledFuture<?> pingTask;

    public MarketChannelClient(PolymarketTradingProperties properties,
                               List<WebSocketEventListener> listeners) {
        this.properties = properties;
        this.listeners = listeners;
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "polymarket-ws-market");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Opens the WebSocket connection to the market channel.
     */
    public void connect() {
        if (shutdown.get()) {
            return;
        }
        String url = properties.getWebsocket().getUrl() + "/market";
        log.info("Connecting to market channel: {}", url);
        Request request = new Request.Builder().url(url).build();
        webSocket = httpClient.newWebSocket(request, this);
    }

    /**
     * Subscribes to market data for the given asset IDs.
     */
    public void subscribe(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        subscribedAssetIds.addAll(assetIds);
        if (connected.get() && webSocket != null) {
            sendSubscription(assetIds);
        }
    }

    /**
     * Unsubscribes from market data for the given asset IDs.
     */
    public void unsubscribe(List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        subscribedAssetIds.removeAll(assetIds);
    }

    /**
     * Shuts down this client, closing the WebSocket and releasing resources.
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            log.info("Shutting down market channel client");
            cancelPingTask();
            if (webSocket != null) {
                webSocket.close(1000, "shutdown");
            }
            scheduler.shutdownNow();
            httpClient.dispatcher().executorService().shutdownNow();
            httpClient.connectionPool().evictAll();
        }
    }

    // ── WebSocketListener callbacks ────────────────────────────────────

    @Override
    public void onOpen(WebSocket ws, Response response) {
        log.info("Market channel connected");
        connected.set(true);
        reconnectAttempt.set(0);
        startPingTask();

        // Re-subscribe with current asset IDs
        if (!subscribedAssetIds.isEmpty()) {
            sendSubscription(new ArrayList<>(subscribedAssetIds));
        }

        for (WebSocketEventListener listener : listeners) {
            try {
                listener.onConnected(ChannelType.MARKET);
            } catch (Exception e) {
                log.warn("Listener onConnected error", e);
            }
        }
    }

    @Override
    public void onMessage(WebSocket ws, String text) {
        if ("PONG".equals(text)) {
            return;
        }
        try {
            JSONObject json = JSON.parseObject(text);
            if (json == null) {
                return;
            }
            String eventType = json.getString("event_type");
            if (eventType == null) {
                return;
            }
            dispatchEvent(eventType, json);
        } catch (Exception e) {
            log.warn("Failed to parse market channel message: {}", text, e);
        }
    }

    @Override
    public void onClosing(WebSocket ws, int code, String reason) {
        log.info("Market channel closing: code={}, reason={}", code, reason);
    }

    @Override
    public void onClosed(WebSocket ws, int code, String reason) {
        log.info("Market channel closed: code={}, reason={}", code, reason);
        connected.set(false);
        cancelPingTask();
        notifyDisconnected(null);
        scheduleReconnect();
    }

    @Override
    public void onFailure(WebSocket ws, Throwable t, Response response) {
        log.warn("Market channel failure", t);
        connected.set(false);
        cancelPingTask();
        notifyDisconnected(t);
        scheduleReconnect();
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private void sendSubscription(List<String> assetIds) {
        JSONObject msg = new JSONObject();
        msg.put("assets_ids", assetIds);
        msg.put("type", "market");
        msg.put("initial_dump", true);
        msg.put("level", 2);
        String payload = msg.toJSONString();
        log.debug("Market subscribe: {}", payload);
        webSocket.send(payload);
    }

    private void dispatchEvent(String eventType, JSONObject json) {
        switch (eventType) {
            case "book":
                com.gary.polymarket.trading.dto.event.BookEvent bookEvent =
                        json.toJavaObject(com.gary.polymarket.trading.dto.event.BookEvent.class);
                for (WebSocketEventListener l : listeners) {
                    try { l.onBook(bookEvent); } catch (Exception e) { log.warn("Listener error", e); }
                }
                break;
            case "price_change":
                com.gary.polymarket.trading.dto.event.PriceChangeEvent priceChangeEvent =
                        json.toJavaObject(com.gary.polymarket.trading.dto.event.PriceChangeEvent.class);
                for (WebSocketEventListener l : listeners) {
                    try { l.onPriceChange(priceChangeEvent); } catch (Exception e) { log.warn("Listener error", e); }
                }
                break;
            case "last_trade_price":
                com.gary.polymarket.trading.dto.event.LastTradePriceEvent lastTradePriceEvent =
                        json.toJavaObject(com.gary.polymarket.trading.dto.event.LastTradePriceEvent.class);
                for (WebSocketEventListener l : listeners) {
                    try { l.onLastTradePrice(lastTradePriceEvent); } catch (Exception e) { log.warn("Listener error", e); }
                }
                break;
            case "tick_size_change":
                com.gary.polymarket.trading.dto.event.TickSizeChangeEvent tickSizeChangeEvent =
                        json.toJavaObject(com.gary.polymarket.trading.dto.event.TickSizeChangeEvent.class);
                for (WebSocketEventListener l : listeners) {
                    try { l.onTickSizeChange(tickSizeChangeEvent); } catch (Exception e) { log.warn("Listener error", e); }
                }
                break;
            case "new_market":
                com.gary.polymarket.trading.dto.event.NewMarketEvent newMarketEvent =
                        json.toJavaObject(com.gary.polymarket.trading.dto.event.NewMarketEvent.class);
                for (WebSocketEventListener l : listeners) {
                    try { l.onNewMarket(newMarketEvent); } catch (Exception e) { log.warn("Listener error", e); }
                }
                break;
            case "market_resolved":
                com.gary.polymarket.trading.dto.event.MarketResolvedEvent marketResolvedEvent =
                        json.toJavaObject(com.gary.polymarket.trading.dto.event.MarketResolvedEvent.class);
                for (WebSocketEventListener l : listeners) {
                    try { l.onMarketResolved(marketResolvedEvent); } catch (Exception e) { log.warn("Listener error", e); }
                }
                break;
            default:
                log.debug("Unknown market event type: {}", eventType);
                break;
        }
    }

    private void startPingTask() {
        cancelPingTask();
        long intervalMs = properties.getWebsocket().getPingInterval().toMillis();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            if (connected.get() && webSocket != null) {
                webSocket.send("PING");
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void cancelPingTask() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
    }

    private void scheduleReconnect() {
        if (shutdown.get()) {
            return;
        }
        int attempt = reconnectAttempt.incrementAndGet();
        long delayMs = properties.getWebsocket().getReconnectInterval().toMillis();
        log.info("Scheduling market channel reconnect attempt {} in {}ms", attempt, delayMs);

        for (WebSocketEventListener listener : listeners) {
            try {
                listener.onReconnecting(ChannelType.MARKET, attempt);
            } catch (Exception e) {
                log.warn("Listener onReconnecting error", e);
            }
        }

        scheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
    }

    private void notifyDisconnected(Throwable cause) {
        for (WebSocketEventListener listener : listeners) {
            try {
                listener.onDisconnected(ChannelType.MARKET, cause);
            } catch (Exception e) {
                log.warn("Listener onDisconnected error", e);
            }
        }
    }
}
