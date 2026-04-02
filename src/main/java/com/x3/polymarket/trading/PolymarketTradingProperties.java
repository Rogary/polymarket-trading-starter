package com.x3.polymarket.trading;

import com.x3.polymarket.trading.enums.SignatureType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "polymarket.trading")
public class PolymarketTradingProperties {

    private String baseUrl = "https://clob.polymarket.com";
    private int chainId = 137;
    private String privateKey;
    private SignatureType signatureType = SignatureType.EOA;
    private String funderAddress;
    private String apiKey;
    private String apiSecret;
    private String apiPassphrase;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(20);
    private Heartbeat heartbeat = new Heartbeat();
    private WebSocket websocket = new WebSocket();

    @Data
    public static class Heartbeat {
        private boolean enabled = true;
        private Duration interval = Duration.ofSeconds(5);
    }

    @Data
    public static class WebSocket {
        private boolean enabled = true;
        private String url = "wss://ws-subscriptions-clob.polymarket.com/ws";
        private Duration reconnectInterval = Duration.ofSeconds(5);
        private Duration pingInterval = Duration.ofSeconds(10);
    }
}
