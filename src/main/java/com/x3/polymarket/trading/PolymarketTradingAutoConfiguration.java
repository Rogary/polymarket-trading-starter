package com.x3.polymarket.trading;

import com.x3.polymarket.trading.auth.L1Authenticator;
import com.x3.polymarket.trading.auth.L2Authenticator;
import com.x3.polymarket.trading.client.PolymarketTradingClient;
import com.x3.polymarket.trading.heartbeat.HeartbeatScheduler;
import com.x3.polymarket.trading.signing.OrderSigner;
import com.x3.polymarket.trading.websocket.PolymarketWebSocketManager;
import com.x3.polymarket.trading.websocket.WebSocketEventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableConfigurationProperties(PolymarketTradingProperties.class)
@ConditionalOnProperty(prefix = "polymarket.trading", name = "private-key")
public class PolymarketTradingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OrderSigner polymarketOrderSigner(PolymarketTradingProperties properties) {
        return new OrderSigner(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public L1Authenticator polymarketL1Authenticator(PolymarketTradingProperties properties,
                                                      OrderSigner orderSigner) {
        return new L1Authenticator(properties, orderSigner);
    }

    @Bean
    @ConditionalOnMissingBean
    public L2Authenticator polymarketL2Authenticator(L1Authenticator l1Authenticator,
                                                      OrderSigner orderSigner) {
        return new L2Authenticator(l1Authenticator, orderSigner.getMakerAddress());
    }

    @Bean
    @ConditionalOnMissingBean
    public PolymarketTradingClient polymarketTradingClient(PolymarketTradingProperties properties,
                                                            L1Authenticator l1Authenticator,
                                                            L2Authenticator l2Authenticator,
                                                            OrderSigner orderSigner) {
        return new PolymarketTradingClient(properties, l1Authenticator, l2Authenticator, orderSigner);
    }

    @Bean
    @ConditionalOnProperty(prefix = "polymarket.trading.heartbeat", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public HeartbeatScheduler polymarketHeartbeatScheduler(PolymarketTradingClient tradingClient,
                                                           PolymarketTradingProperties properties) {
        return new HeartbeatScheduler(tradingClient, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "polymarket.trading.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public PolymarketWebSocketManager polymarketWebSocketManager(
            PolymarketTradingProperties properties,
            L1Authenticator l1Authenticator,
            List<WebSocketEventListener> listeners) {
        if (listeners == null) {
            listeners = Collections.emptyList();
        }
        return new PolymarketWebSocketManager(properties, l1Authenticator, listeners);
    }
}
