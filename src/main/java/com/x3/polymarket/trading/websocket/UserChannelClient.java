package com.x3.polymarket.trading.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.x3.polymarket.trading.PolymarketTradingProperties;
import com.x3.polymarket.trading.auth.ApiCredentials;
import com.x3.polymarket.trading.auth.L1Authenticator;
import com.x3.polymarket.trading.enums.ChannelType;
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
 * WebSocket client for the Polymarket user channel.
 * Receives authenticated order and trade update events.
 */
public class UserChannelClient extends WebSocketListener {

    private static final Logger log = LoggerFactory.getLogger(UserChannelClient.class);

    private final PolymarketTradingProperties properties;
    private final L1Authenticator l1Authenticator;
    private final List<WebSocketEventListener> listeners;
    private final CopyOnWriteArraySet<String> subscribedMarketIds = new CopyOnWriteArraySet<>();
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    private volatile WebSocket webSocket;
    private volatile ScheduledFuture<?> pingTask;

    public UserChannelClient(PolymarketTradingProperties properties,
                             L1Authenticator l1Authenticator,
                             List<WebSocketEventListener> listeners) {
        this.properties = properties;
        this.l1Authenticator = l1Authenticator;
        this.listeners = listeners;
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "polymarket-ws-user");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Opens the WebSocket connection to the user channel.
     */
    public void connect() {
        if (shutdown.get()) {
            return;
        }
        String url = properties.getWebsocket().getUrl() + "/user";
        log.info("Connecting to user channel: {}", url);
        Request request = new Request.Builder().url(url).build();
        webSocket = httpClient.newWebSocket(request, this);
    }

    /**
     * Subscribes to user events for the given market IDs.
     */
    public void subscribe(List<String> marketIds) {
        if (marketIds == null || marketIds.isEmpty()) {
            return;
        }
        subscribedMarketIds.addAll(marketIds);
        if (connected.get() && webSocket != null) {
            sendSubscription(new ArrayList<>(subscribedMarketIds));
        }
    }

    /**
     * Unsubscribes from user events for the given market IDs.
     */
    public void unsubscribe(List<String> marketIds) {
        if (marketIds == null || marketIds.isEmpty()) {
            return;
        }
        subscribedMarketIds.removeAll(marketIds);
    }

    /**
     * Shuts down this client, closing the WebSocket and releasing resources.
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            log.info("Shutting down user channel client");
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
        log.info("User channel connected");
        connected.set(true);
        reconnectAttempt.set(0);
        startPingTask();

        // Re-subscribe with current market IDs
        if (!subscribedMarketIds.isEmpty()) {
            sendSubscription(new ArrayList<>(subscribedMarketIds));
        }

        for (WebSocketEventListener listener : listeners) {
            try {
                listener.onConnected(ChannelType.USER);
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
            log.warn("Failed to parse user channel message: {}", text, e);
        }
    }

    @Override
    public void onClosing(WebSocket ws, int code, String reason) {
        log.info("User channel closing: code={}, reason={}", code, reason);
    }

    @Override
    public void onClosed(WebSocket ws, int code, String reason) {
        log.info("User channel closed: code={}, reason={}", code, reason);
        connected.set(false);
        cancelPingTask();
        notifyDisconnected(null);
        scheduleReconnect();
    }

    @Override
    public void onFailure(WebSocket ws, Throwable t, Response response) {
        log.warn("User channel failure", t);
        connected.set(false);
        cancelPingTask();
        notifyDisconnected(t);
        scheduleReconnect();
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private void sendSubscription(List<String> marketIds) {
        ApiCredentials credentials = l1Authenticator.getCredentials();

        JSONObject auth = new JSONObject();
        auth.put("apiKey", credentials.getApiKey());
        auth.put("secret", credentials.getSecret());
        auth.put("passphrase", credentials.getPassphrase());

        JSONObject msg = new JSONObject();
        msg.put("auth", auth);
        msg.put("type", "user");
        msg.put("markets", marketIds);

        String payload = msg.toJSONString();
        log.debug("User channel subscribed to {} markets", marketIds.size());
        webSocket.send(payload);
    }

    private void dispatchEvent(String eventType, JSONObject json) {
        switch (eventType) {
            case "order":
                com.x3.polymarket.trading.dto.event.OrderUpdateEvent orderEvent =
                        json.toJavaObject(com.x3.polymarket.trading.dto.event.OrderUpdateEvent.class);
                for (WebSocketEventListener l : listeners) {
                    try { l.onOrderUpdate(orderEvent); } catch (Exception e) { log.warn("Listener error", e); }
                }
                break;
            case "trade":
                com.x3.polymarket.trading.dto.event.TradeUpdateEvent tradeEvent =
                        json.toJavaObject(com.x3.polymarket.trading.dto.event.TradeUpdateEvent.class);
                for (WebSocketEventListener l : listeners) {
                    try { l.onTradeUpdate(tradeEvent); } catch (Exception e) { log.warn("Listener error", e); }
                }
                break;
            default:
                log.debug("Unknown user event type: {}", eventType);
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
        log.info("Scheduling user channel reconnect attempt {} in {}ms", attempt, delayMs);

        for (WebSocketEventListener listener : listeners) {
            try {
                listener.onReconnecting(ChannelType.USER, attempt);
            } catch (Exception e) {
                log.warn("Listener onReconnecting error", e);
            }
        }

        scheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
    }

    private void notifyDisconnected(Throwable cause) {
        for (WebSocketEventListener listener : listeners) {
            try {
                listener.onDisconnected(ChannelType.USER, cause);
            } catch (Exception e) {
                log.warn("Listener onDisconnected error", e);
            }
        }
    }
}
