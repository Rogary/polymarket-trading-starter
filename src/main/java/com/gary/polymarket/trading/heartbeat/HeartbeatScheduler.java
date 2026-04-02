package com.gary.polymarket.trading.heartbeat;

import com.gary.polymarket.trading.PolymarketTradingProperties;
import com.gary.polymarket.trading.client.PolymarketTradingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class HeartbeatScheduler implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final PolymarketTradingClient tradingClient;
    private final long intervalMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private ScheduledExecutorService executor;

    public HeartbeatScheduler(PolymarketTradingClient tradingClient,
                               PolymarketTradingProperties properties) {
        this.tradingClient = tradingClient;
        this.intervalMs = properties.getHeartbeat().getInterval().toMillis();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "polymarket-heartbeat");
                t.setDaemon(true);
                return t;
            });
            executor.scheduleAtFixedRate(this::doHeartbeat, 0, intervalMs, TimeUnit.MILLISECONDS);
            log.info("Polymarket heartbeat started, interval={}ms", intervalMs);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (executor != null) executor.shutdownNow();
            log.info("Polymarket heartbeat stopped");
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    @Override
    public int getPhase() { return Integer.MAX_VALUE - 1; }

    private void doHeartbeat() {
        try {
            tradingClient.sendHeartbeat();
            int prev = consecutiveFailures.getAndSet(0);
            if (prev > 0) {
                log.info("Polymarket heartbeat recovered after {} consecutive failures", prev);
            }
        } catch (Exception e) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                log.error("Polymarket heartbeat failed {} consecutive times! Open orders may be cancelled.", failures, e);
            } else {
                log.warn("Polymarket heartbeat failed (attempt {})", failures, e);
            }
        }
    }
}
