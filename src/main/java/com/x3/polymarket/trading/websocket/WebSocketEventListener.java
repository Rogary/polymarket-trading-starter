package com.x3.polymarket.trading.websocket;

import com.x3.polymarket.trading.dto.event.*;
import com.x3.polymarket.trading.enums.ChannelType;

/**
 * Callback interface for Polymarket WebSocket events.
 * Implement only the methods you care about — all have empty defaults.
 */
public interface WebSocketEventListener {

    // ── Market channel events ──────────────────────────────────────────

    default void onBook(BookEvent event) {}

    default void onPriceChange(PriceChangeEvent event) {}

    default void onLastTradePrice(LastTradePriceEvent event) {}

    default void onTickSizeChange(TickSizeChangeEvent event) {}

    default void onNewMarket(NewMarketEvent event) {}

    default void onMarketResolved(MarketResolvedEvent event) {}

    // ── User channel events ────────────────────────────────────────────

    default void onOrderUpdate(OrderUpdateEvent event) {}

    default void onTradeUpdate(TradeUpdateEvent event) {}

    // ── Connection lifecycle ───────────────────────────────────────────

    default void onConnected(ChannelType channel) {}

    default void onDisconnected(ChannelType channel, Throwable cause) {}

    default void onReconnecting(ChannelType channel, int attempt) {}
}
