**English** | [中文](README-zh.md)

# polymarket-trading-spring-boot-starter

A Spring Boot Starter for the Polymarket CLOB Trading API — provides auto-configured EIP-712 order signing, two-layer authentication (L1 wallet + L2 HMAC), a REST trading client, and WebSocket market data on Polygon mainnet.

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.gary</groupId>
    <artifactId>polymarket-trading-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configuration

```yaml
polymarket:
  trading:
    private-key: "0xYOUR_PRIVATE_KEY"
    # Optional: preset API credentials to skip L1 derivation
    # api-key: ""
    # api-secret: ""
    # api-passphrase: ""
```

Setting `private-key` activates the starter. All other properties have sensible defaults.

### 3. Usage

```java
@Autowired
private PolymarketTradingClient tradingClient;

@Autowired
private PolymarketWebSocketManager webSocketManager;

// Place an order
PlaceOrderRequest request = new PlaceOrderRequest()
        .setTokenId("TOKEN_ID")
        .setSide(Side.BUY)
        .setPrice(new BigDecimal("0.50"))
        .setSize(new BigDecimal("100"))
        .setOrderType(OrderType.GTC);
OrderResponse response = tradingClient.placeOrder(request);

// Subscribe to market data
webSocketManager.subscribeMarket(List.of("ASSET_ID"));
```

## Features

- **Order Signing** — EIP-712 typed data signing via web3j, supporting EOA / POLY_PROXY / GNOSIS_SAFE signature types
- **L1 Authentication** — Wallet-based ClobAuth signing, derives API credentials from `/auth/derive-api-key`
- **L2 Authentication** — Per-request HMAC-SHA256 signing for REST calls
- **REST Trading Client** — Place/cancel orders (single and batch, up to 15), query active orders and trades, heartbeat
- **WebSocket** — Market channel (order book, price changes, trades) and user channel (order/trade updates), with auto-reconnect
- **Heartbeat** — Configurable keep-alive scheduler (default 5s interval)
- **Spring Boot Integration** — All beans use `@ConditionalOnMissingBean` for easy customization

## Configuration Properties

All properties under `polymarket.trading.*`:

| Property | Default | Description |
|---|---|---|
| `private-key` | — | **Required.** Hex private key (with or without 0x prefix) |
| `base-url` | `https://clob.polymarket.com` | CLOB API base URL |
| `chain-id` | `137` | Polygon mainnet |
| `signature-type` | `EOA` | `EOA`, `POLY_PROXY`, or `GNOSIS_SAFE` |
| `funder-address` | — | Required when signature-type is POLY_PROXY or GNOSIS_SAFE |
| `heartbeat.enabled` | `true` | Enable heartbeat scheduler |
| `heartbeat.interval` | `5s` | Heartbeat interval |
| `websocket.enabled` | `true` | Enable WebSocket connections |
| `websocket.url` | `wss://ws-subscriptions-clob.polymarket.com/ws` | WebSocket endpoint |
| `websocket.reconnect-interval` | `5s` | Reconnect delay |
| `websocket.ping-interval` | `10s` | Ping interval |

## Build

```bash
mvn clean install
```

Requires Java 11+.

## Tech Stack

- Java 11, Spring Boot 2.7
- web3j 4.10.3 (EIP-712 signing)
- OkHttp3 (HTTP + WebSocket)
- FastJSON2 (JSON serialization)
- Lombok
