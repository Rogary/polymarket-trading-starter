[English](README.md) | **中文**

# polymarket-trading-spring-boot-starter

Polymarket CLOB 交易 API 的 Spring Boot Starter —— 提供自动配置的 EIP-712 订单签名、双层认证（L1 钱包 + L2 HMAC）、REST 交易客户端，以及基于 Polygon 主网的 WebSocket 行情数据支持。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.gary</groupId>
    <artifactId>polymarket-trading-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置

```yaml
polymarket:
  trading:
    private-key: "0xYOUR_PRIVATE_KEY"
    # 可选：预设 API 凭证以跳过 L1 派生
    # api-key: ""
    # api-secret: ""
    # api-passphrase: ""
```

设置 `private-key` 即可激活 Starter，其余属性均有合理默认值。

### 3. 使用

```java
@Autowired
private PolymarketTradingClient tradingClient;

@Autowired
private PolymarketWebSocketManager webSocketManager;

// 下单
PlaceOrderRequest request = new PlaceOrderRequest()
        .setTokenId("TOKEN_ID")
        .setSide(Side.BUY)
        .setPrice(new BigDecimal("0.50"))
        .setSize(new BigDecimal("100"))
        .setOrderType(OrderType.GTC);
OrderResponse response = tradingClient.placeOrder(request);

// 订阅行情数据
webSocketManager.subscribeMarket(List.of("ASSET_ID"));
```

## 功能特性

- **订单签名** —— 通过 web3j 进行 EIP-712 类型化数据签名，支持 EOA / POLY_PROXY / GNOSIS_SAFE 签名类型
- **L1 认证** —— 基于钱包的 ClobAuth 签名，从 `/auth/derive-api-key` 派生 API 凭证
- **L2 认证** —— 每次 REST 请求的 HMAC-SHA256 签名
- **REST 交易客户端** —— 下单/撤单（单笔和批量，最多 15 笔）、查询活跃订单和成交记录、心跳
- **WebSocket** —— 行情频道（订单簿、价格变动、成交）和用户频道（订单/成交更新），支持自动重连
- **心跳** —— 可配置的保活调度器（默认 5 秒间隔）
- **Spring Boot 集成** —— 所有 Bean 均使用 `@ConditionalOnMissingBean`，方便自定义覆盖

## 配置属性

所有属性位于 `polymarket.trading.*` 下：

| 属性 | 默认值 | 说明 |
|---|---|---|
| `private-key` | — | **必填。** 十六进制私钥（带或不带 0x 前缀） |
| `base-url` | `https://clob.polymarket.com` | CLOB API 基础 URL |
| `chain-id` | `137` | Polygon 主网 |
| `signature-type` | `EOA` | `EOA`、`POLY_PROXY` 或 `GNOSIS_SAFE` |
| `funder-address` | — | 当 signature-type 为 POLY_PROXY 或 GNOSIS_SAFE 时必填 |
| `heartbeat.enabled` | `true` | 启用心跳调度器 |
| `heartbeat.interval` | `5s` | 心跳间隔 |
| `websocket.enabled` | `true` | 启用 WebSocket 连接 |
| `websocket.url` | `wss://ws-subscriptions-clob.polymarket.com/ws` | WebSocket 端点 |
| `websocket.reconnect-interval` | `5s` | 重连延迟 |
| `websocket.ping-interval` | `10s` | Ping 间隔 |

## 构建

```bash
mvn clean install
```

需要 Java 11+。

## 技术栈

- Java 11, Spring Boot 2.7
- web3j 4.10.3（EIP-712 签名）
- OkHttp3（HTTP + WebSocket）
- FastJSON2（JSON 序列化）
- Lombok
