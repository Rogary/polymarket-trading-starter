# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot Starter wrapping the Polymarket CLOB (Central Limit Order Book) Trading API. Provides auto-configured beans for EIP-712 order signing, two-tier authentication (L1 wallet-based + L2 HMAC), REST trading, and WebSocket market data. Targets Polygon mainnet (chain ID 137).

GAV: `com.x3:polymarket-trading-spring-boot-starter:1.0.0`

## Build

```bash
mvn clean install
```

Java 11, Spring Boot 2.7.18. No test infrastructure exists.

## Architecture

### Auto-Configuration & Bean Wiring

`PolymarketTradingAutoConfiguration` activates when `polymarket.trading.private-key` is set. Bean creation order matters due to dependencies:

```
OrderSigner (private key → ECKeyPair, derives maker/signer addresses)
  → L1Authenticator (EIP-712 ClobAuth signing → derives API credentials from /auth/derive-api-key)
    → L2Authenticator (HMAC-SHA256 signing of every REST request using L1-derived credentials)
      → PolymarketTradingClient (REST client: orders, cancellations, queries, heartbeats)
```

Conditional beans (enabled by default, can be disabled):
- `HeartbeatScheduler` — `polymarket.trading.heartbeat.enabled` — SmartLifecycle, 5s interval POST to `/heartbeats`
- `PolymarketWebSocketManager` — `polymarket.trading.websocket.enabled` — SmartLifecycle, manages `MarketChannelClient` + `UserChannelClient`

All beans use `@ConditionalOnMissingBean` so consumers can override any component.

### Authentication Flow

Two-tier auth, both required for trading:

1. **L1 (wallet auth)**: `OrderSigner.signClobAuthMessage()` creates an EIP-712 signature → `L1Authenticator` POSTs to `/auth/derive-api-key` with `POLY_ADDRESS`, `POLY_SIGNATURE`, `POLY_TIMESTAMP`, `POLY_NONCE` headers → receives `apiKey`, `secret`, `passphrase`. Credentials are cached in an `AtomicReference`; can be pre-configured via properties to skip derivation.

2. **L2 (HMAC auth)**: `L2Authenticator.sign()` computes `HMAC-SHA256(base64Decode(secret), timestamp + METHOD + path + body)` → adds `POLY_ADDRESS`, `POLY_SIGNATURE`, `POLY_TIMESTAMP`, `POLY_API_KEY`, `POLY_PASSPHRASE` headers to every REST request.

### Order Signing

`OrderSigner.buildAndSign()` converts `PlaceOrderRequest` (human-readable price/size as `BigDecimal`) into on-chain format:
- Amounts scaled by 1e6 (USDC decimals)
- BUY: makerAmount = price×size×1e6, takerAmount = size×1e6
- SELL: makerAmount = size×1e6, takerAmount = price×size×1e6
- Uses `negRisk` flag to select between `CTF_EXCHANGE` and `NEG_RISK_CTF_EXCHANGE` contract addresses
- Signs via web3j `StructuredDataEncoder` → 65-byte (r+s+v) hex signature

### WebSocket

`PolymarketWebSocketManager` (SmartLifecycle, phase MAX_VALUE-2) manages two OkHttp WebSocket connections:
- `MarketChannelClient` — public market data (order books, price changes, trades)
- `UserChannelClient` — authenticated user events (order updates, trade fills); requires L1 auth headers

Consumers implement `WebSocketEventListener` interface and are auto-injected as a `List<WebSocketEventListener>`.

## Key Conventions

- **JSON**: FastJSON2 (not Jackson) — all serialization/deserialization uses `com.alibaba.fastjson2.JSON`
- **HTTP**: OkHttp3 — both REST client and WebSocket connections
- **Crypto**: web3j 4.10.3 for `ECKeyPair`, `StructuredDataEncoder`, EIP-712 signing
- **DTOs**: Lombok `@Data` + `@Accessors(chain = true)` on response types; `Serializable` on request types
- **Exceptions**: Hierarchy under `PolymarketTradingException` — `AuthException`, `SigningException`, `OrderException`, `RateLimitException`, `WebSocketException`
- **Batch limit**: Max 15 orders per `placeOrders()` call (enforced in `PolymarketTradingClient`)
- **Contract addresses**: Hardcoded in `ContractAddresses.java` for Polygon mainnet

## Configuration Properties

All under `polymarket.trading.*`:

| Property | Default | Required | Notes |
|---|---|---|---|
| `private-key` | — | Yes | Activation gate; hex with or without 0x prefix |
| `base-url` | `https://clob.polymarket.com` | No | |
| `chain-id` | `137` | No | Polygon mainnet |
| `signature-type` | `EOA` | No | `EOA`, `POLY_PROXY`, or `GNOSIS_SAFE` |
| `funder-address` | — | If POLY_PROXY/GNOSIS_SAFE | Maker address override |
| `api-key` / `api-secret` / `api-passphrase` | — | No | Pre-set to skip L1 derivation |
| `heartbeat.enabled` | `true` | No | |
| `heartbeat.interval` | `5s` | No | |
| `websocket.enabled` | `true` | No | |
| `websocket.url` | `wss://ws-subscriptions-clob.polymarket.com/ws` | No | |
| `websocket.reconnect-interval` | `5s` | No | |
| `websocket.ping-interval` | `10s` | No | |
