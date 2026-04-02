# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指导。

## 项目概览

封装 Polymarket CLOB（中央限价订单簿）交易 API 的 Spring Boot Starter。提供自动配置的 EIP-712 订单签名、双层认证（L1 钱包 + L2 HMAC）、REST 交易和 WebSocket 行情数据等 Bean。目标网络为 Polygon 主网（chain ID 137）。

GAV: `com.gary:polymarket-trading-spring-boot-starter:1.0.0`

## 构建

```bash
mvn clean install
```

Java 11, Spring Boot 2.7.18。无测试基础设施。

## 架构

### 自动配置与 Bean 装配

`PolymarketTradingAutoConfiguration` 在设置 `polymarket.trading.private-key` 时激活。Bean 创建顺序因依赖关系而重要：

```
OrderSigner（私钥 → ECKeyPair，派生 maker/signer 地址）
  → L1Authenticator（EIP-712 ClobAuth 签名 → 从 /auth/derive-api-key 派生 API 凭证）
    → L2Authenticator（使用 L1 派生凭证对每个 REST 请求进行 HMAC-SHA256 签名）
      → PolymarketTradingClient（REST 客户端：下单、撤单、查询、心跳）
```

条件 Bean（默认启用，可禁用）：
- `HeartbeatScheduler` —— `polymarket.trading.heartbeat.enabled` —— SmartLifecycle，5 秒间隔 POST 至 `/heartbeats`
- `PolymarketWebSocketManager` —— `polymarket.trading.websocket.enabled` —— SmartLifecycle，管理 `MarketChannelClient` + `UserChannelClient`

所有 Bean 均使用 `@ConditionalOnMissingBean`，消费方可覆盖任意组件。

### 认证流程

双层认证，交易时两层均需使用：

1. **L1（钱包认证）**：`OrderSigner.signClobAuthMessage()` 创建 EIP-712 签名 → `L1Authenticator` 携带 `POLY_ADDRESS`、`POLY_SIGNATURE`、`POLY_TIMESTAMP`、`POLY_NONCE` 请求头 POST 至 `/auth/derive-api-key` → 获得 `apiKey`、`secret`、`passphrase`。凭证缓存在 `AtomicReference` 中；也可通过配置属性预设以跳过派生。

2. **L2（HMAC 认证）**：`L2Authenticator.sign()` 计算 `HMAC-SHA256(base64Decode(secret), timestamp + METHOD + path + body)` → 在每个 REST 请求中添加 `POLY_ADDRESS`、`POLY_SIGNATURE`、`POLY_TIMESTAMP`、`POLY_API_KEY`、`POLY_PASSPHRASE` 请求头。

### 订单签名

`OrderSigner.buildAndSign()` 将 `PlaceOrderRequest`（人类可读的 `BigDecimal` 价格/数量）转换为链上格式：
- 金额按 1e6 缩放（USDC 精度）
- 买入：makerAmount = price×size×1e6，takerAmount = size×1e6
- 卖出：makerAmount = size×1e6，takerAmount = price×size×1e6
- 使用 `negRisk` 标志在 `CTF_EXCHANGE` 和 `NEG_RISK_CTF_EXCHANGE` 合约地址之间选择
- 通过 web3j `StructuredDataEncoder` 签名 → 65 字节 (r+s+v) 十六进制签名

### WebSocket

`PolymarketWebSocketManager`（SmartLifecycle，phase MAX_VALUE-2）管理两个 OkHttp WebSocket 连接：
- `MarketChannelClient` —— 公开行情数据（订单簿、价格变动、成交）
- `UserChannelClient` —— 认证用户事件（订单更新、成交回报）；需要 L1 认证请求头

消费方实现 `WebSocketEventListener` 接口，自动注入为 `List<WebSocketEventListener>`。

## 关键约定

- **JSON**：FastJSON2（非 Jackson）—— 所有序列化/反序列化使用 `com.alibaba.fastjson2.JSON`
- **HTTP**：OkHttp3 —— REST 客户端和 WebSocket 连接均使用
- **加密**：web3j 4.10.3，用于 `ECKeyPair`、`StructuredDataEncoder`、EIP-712 签名
- **DTO**：响应类型使用 Lombok `@Data` + `@Accessors(chain = true)`；请求类型实现 `Serializable`
- **异常**：层次结构位于 `PolymarketTradingException` 下 —— `AuthException`、`SigningException`、`OrderException`、`RateLimitException`、`WebSocketException`
- **批量限制**：`placeOrders()` 每次最多 15 笔订单（在 `PolymarketTradingClient` 中强制执行）
- **合约地址**：硬编码在 `ContractAddresses.java` 中，适用于 Polygon 主网

## 配置属性

所有属性位于 `polymarket.trading.*` 下：

| 属性 | 默认值 | 是否必填 | 说明 |
|---|---|---|---|
| `private-key` | — | 是 | 激活开关；十六进制，带或不带 0x 前缀 |
| `base-url` | `https://clob.polymarket.com` | 否 | |
| `chain-id` | `137` | 否 | Polygon 主网 |
| `signature-type` | `EOA` | 否 | `EOA`、`POLY_PROXY` 或 `GNOSIS_SAFE` |
| `funder-address` | — | POLY_PROXY/GNOSIS_SAFE 时必填 | Maker 地址覆盖 |
| `api-key` / `api-secret` / `api-passphrase` | — | 否 | 预设以跳过 L1 派生 |
| `heartbeat.enabled` | `true` | 否 | |
| `heartbeat.interval` | `5s` | 否 | |
| `websocket.enabled` | `true` | 否 | |
| `websocket.url` | `wss://ws-subscriptions-clob.polymarket.com/ws` | 否 | |
| `websocket.reconnect-interval` | `5s` | 否 | |
| `websocket.ping-interval` | `10s` | 否 | |
