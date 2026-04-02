package com.x3.polymarket.trading.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.x3.polymarket.trading.PolymarketTradingProperties;
import com.x3.polymarket.trading.auth.ApiCredentials;
import com.x3.polymarket.trading.auth.L1Authenticator;
import com.x3.polymarket.trading.auth.L2Authenticator;
import com.x3.polymarket.trading.dto.request.PlaceOrderRequest;
import com.x3.polymarket.trading.dto.request.OrderQueryRequest;
import com.x3.polymarket.trading.dto.request.TradeQueryRequest;
import com.x3.polymarket.trading.dto.response.OpenOrder;
import com.x3.polymarket.trading.dto.response.OrderResponse;
import com.x3.polymarket.trading.dto.response.PageResult;
import com.x3.polymarket.trading.dto.response.SignedOrder;
import com.x3.polymarket.trading.dto.response.Trade;
import com.x3.polymarket.trading.exception.PolymarketOrderException;
import com.x3.polymarket.trading.exception.PolymarketRateLimitException;
import com.x3.polymarket.trading.exception.PolymarketTradingException;
import com.x3.polymarket.trading.signing.OrderSigner;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST client for the Polymarket CLOB trading API.
 * Handles order placement, cancellation, queries, and heartbeats.
 */
public class PolymarketTradingClient {

    private static final Logger log = LoggerFactory.getLogger(PolymarketTradingClient.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_BATCH_SIZE = 15;

    private final PolymarketTradingProperties properties;
    private final L1Authenticator l1Authenticator;
    private final L2Authenticator l2Authenticator;
    private final OrderSigner orderSigner;
    private final OkHttpClient httpClient;

    public PolymarketTradingClient(PolymarketTradingProperties properties,
                                   L1Authenticator l1Authenticator,
                                   L2Authenticator l2Authenticator,
                                   OrderSigner orderSigner) {
        this.properties = properties;
        this.l1Authenticator = l1Authenticator;
        this.l2Authenticator = l2Authenticator;
        this.orderSigner = orderSigner;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Places a single order on Polymarket.
     *
     * @param request the order request
     * @return the order response
     */
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        String feeRateBps = fetchFeeRate(request.getTokenId());
        SignedOrder signedOrder = orderSigner.buildAndSign(request, feeRateBps);

        JSONObject body = new JSONObject();
        body.put("order", signedOrder);
        body.put("owner", l1Authenticator.getCredentials().getApiKey());
        body.put("orderType", request.getOrderType().name());

        if (request.getPostOnly() != null && request.getPostOnly()) {
            body.put("postOnly", true);
        }

        return executePost("/order", body.toJSONString(), OrderResponse.class);
    }

    /**
     * Places multiple orders in a single batch request.
     *
     * @param requests list of order requests (max 15)
     * @return list of order responses
     */
    public List<OrderResponse> placeOrders(List<PlaceOrderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }
        if (requests.size() > MAX_BATCH_SIZE) {
            throw new PolymarketOrderException(
                    "Batch size " + requests.size() + " exceeds maximum of " + MAX_BATCH_SIZE, null);
        }

        // Fetch fee rate once using the first order's token ID
        String feeRateBps = fetchFeeRate(requests.get(0).getTokenId());

        JSONArray ordersArray = new JSONArray();
        for (PlaceOrderRequest request : requests) {
            SignedOrder signedOrder = orderSigner.buildAndSign(request, feeRateBps);

            JSONObject orderObj = new JSONObject();
            orderObj.put("order", signedOrder);
            orderObj.put("owner", l1Authenticator.getCredentials().getApiKey());
            orderObj.put("orderType", request.getOrderType().name());

            if (request.getPostOnly() != null && request.getPostOnly()) {
                orderObj.put("postOnly", true);
            }

            ordersArray.add(orderObj);
        }

        return executePost("/orders", ordersArray.toJSONString(),
                new TypeReference<List<OrderResponse>>() {});
    }

    /**
     * Cancels a single order by its ID.
     *
     * @param orderId the order ID to cancel
     */
    public void cancelOrder(String orderId) {
        JSONObject body = new JSONObject();
        body.put("orderID", orderId);
        executeDelete("/order", body.toJSONString());
    }

    /**
     * Cancels multiple orders by their IDs.
     *
     * @param orderIds list of order IDs to cancel
     */
    public void cancelOrders(List<String> orderIds) {
        JSONArray body = new JSONArray();
        for (String id : orderIds) {
            JSONObject item = new JSONObject();
            item.put("id", id);
            body.add(item);
        }
        executeDelete("/orders", body.toJSONString());
    }

    /**
     * Cancels all open orders.
     */
    public void cancelAll() {
        executeDelete("/cancel-all", null);
    }

    /**
     * Cancels all orders for a specific market and/or asset.
     *
     * @param market  the market condition ID
     * @param assetId the asset/token ID
     */
    public void cancelMarketOrders(String market, String assetId) {
        JSONObject body = new JSONObject();
        body.put("market", market);
        body.put("asset_id", assetId);
        executeDelete("/cancel-market-orders", body.toJSONString());
    }

    /**
     * Queries open orders with optional filtering.
     *
     * @param request query parameters
     * @return paginated list of open orders
     */
    public PageResult<OpenOrder> getOpenOrders(OrderQueryRequest request) {
        StringBuilder path = new StringBuilder("/orders");
        String separator = "?";

        if (request.getMarket() != null) {
            path.append(separator).append("market=").append(request.getMarket());
            separator = "&";
        }
        if (request.getAssetId() != null) {
            path.append(separator).append("asset_id=").append(request.getAssetId());
            separator = "&";
        }
        if (request.getNextCursor() != null) {
            path.append(separator).append("next_cursor=").append(request.getNextCursor());
        }

        return executeGet(path.toString(), new TypeReference<PageResult<OpenOrder>>() {});
    }

    /**
     * Gets a single order by its ID.
     *
     * @param orderId the order ID
     * @return the open order details
     */
    public OpenOrder getOrder(String orderId) {
        return executeGet("/order/" + orderId, OpenOrder.class);
    }

    /**
     * Queries trades with optional filtering.
     * The maker_address is always included from the order signer.
     *
     * @param request query parameters
     * @return paginated list of trades
     */
    public PageResult<Trade> getTrades(TradeQueryRequest request) {
        StringBuilder path = new StringBuilder("/trades");
        path.append("?maker_address=").append(orderSigner.getMakerAddress());

        if (request.getMarket() != null) {
            path.append("&market=").append(request.getMarket());
        }
        if (request.getAssetId() != null) {
            path.append("&asset_id=").append(request.getAssetId());
        }
        if (request.getBefore() != null) {
            path.append("&before=").append(request.getBefore());
        }
        if (request.getAfter() != null) {
            path.append("&after=").append(request.getAfter());
        }
        if (request.getNextCursor() != null) {
            path.append("&next_cursor=").append(request.getNextCursor());
        }

        return executeGet(path.toString(), new TypeReference<PageResult<Trade>>() {});
    }

    /**
     * Sends a heartbeat to keep the API session alive.
     */
    public void sendHeartbeat() {
        executePost("/heartbeats", "{}", Void.class);
    }

    /**
     * Derives new API credentials via L1 authentication.
     *
     * @return freshly derived API credentials
     */
    public ApiCredentials deriveApiCredentials() {
        return l1Authenticator.deriveCredentials();
    }

    // --------------- Private helper methods ---------------

    /**
     * Fetches the fee rate for a given token ID. No authentication required.
     * Returns "0" on failure.
     */
    private String fetchFeeRate(String tokenId) {
        String url = properties.getBaseUrl() + "/fee-rate?token_id=" + tokenId;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String bodyStr = responseBody != null ? responseBody.string() : "";

            if (!response.isSuccessful()) {
                log.warn("Failed to fetch fee rate for token {}, HTTP {}: {}", tokenId, response.code(), bodyStr);
                return "0";
            }

            JSONObject json = JSON.parseObject(bodyStr);
            if (json != null && json.containsKey("fee_rate_bps")) {
                return json.getString("fee_rate_bps");
            }

            log.warn("Unexpected fee rate response for token {}: {}", tokenId, bodyStr);
            return "0";
        } catch (IOException e) {
            log.warn("Failed to fetch fee rate for token {}: {}", tokenId, e.getMessage());
            return "0";
        }
    }

    private <T> T executeGet(String path, Class<T> responseType) {
        String url = properties.getBaseUrl() + path;
        Request.Builder builder = new Request.Builder().url(url).get();
        l2Authenticator.sign(builder, "GET", path, "");
        Request request = builder.build();
        return executeRequest(request, responseType);
    }

    private <T> T executeGet(String path, TypeReference<T> typeReference) {
        String url = properties.getBaseUrl() + path;
        Request.Builder builder = new Request.Builder().url(url).get();
        l2Authenticator.sign(builder, "GET", path, "");
        Request request = builder.build();
        return executeRequest(request, typeReference);
    }

    private <T> T executePost(String path, String jsonBody, Class<T> responseType) {
        String url = properties.getBaseUrl() + path;
        RequestBody body = RequestBody.create(jsonBody, JSON_TYPE);
        Request.Builder builder = new Request.Builder().url(url).post(body);
        l2Authenticator.sign(builder, "POST", path, jsonBody);
        Request request = builder.build();
        return executeRequest(request, responseType);
    }

    private <T> T executePost(String path, String jsonBody, TypeReference<T> typeReference) {
        String url = properties.getBaseUrl() + path;
        RequestBody body = RequestBody.create(jsonBody, JSON_TYPE);
        Request.Builder builder = new Request.Builder().url(url).post(body);
        l2Authenticator.sign(builder, "POST", path, jsonBody);
        Request request = builder.build();
        return executeRequest(request, typeReference);
    }

    private void executeDelete(String path, String jsonBody) {
        String url = properties.getBaseUrl() + path;
        Request.Builder builder = new Request.Builder().url(url);
        if (jsonBody != null && !jsonBody.isEmpty()) {
            RequestBody body = RequestBody.create(jsonBody, JSON_TYPE);
            builder.delete(body);
            l2Authenticator.sign(builder, "DELETE", path, jsonBody);
        } else {
            builder.delete();
            l2Authenticator.sign(builder, "DELETE", path, "");
        }
        Request request = builder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String bodyStr = responseBody != null ? responseBody.string() : "";

            if (!response.isSuccessful()) {
                handleErrorResponse(response.code(), bodyStr);
            }
        } catch (PolymarketTradingException e) {
            throw e;
        } catch (IOException e) {
            throw new PolymarketTradingException("HTTP request failed: DELETE " + path, e);
        }
    }

    private <T> T executeRequest(Request request, Class<T> responseType) {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String bodyStr = responseBody != null ? responseBody.string() : "";

            if (!response.isSuccessful()) {
                handleErrorResponse(response.code(), bodyStr);
            }

            if (responseType == Void.class || responseType == void.class) {
                return null;
            }

            return JSON.parseObject(bodyStr, responseType);
        } catch (PolymarketTradingException e) {
            throw e;
        } catch (IOException e) {
            throw new PolymarketTradingException("HTTP request failed: " + request.method() + " " + request.url(), e);
        }
    }

    private <T> T executeRequest(Request request, TypeReference<T> typeReference) {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String bodyStr = responseBody != null ? responseBody.string() : "";

            if (!response.isSuccessful()) {
                handleErrorResponse(response.code(), bodyStr);
            }

            return JSON.parseObject(bodyStr, typeReference);
        } catch (PolymarketTradingException e) {
            throw e;
        } catch (IOException e) {
            throw new PolymarketTradingException("HTTP request failed: " + request.method() + " " + request.url(), e);
        }
    }

    private void handleErrorResponse(int statusCode, String body) {
        if (statusCode == 429) {
            throw new PolymarketRateLimitException("Rate limit exceeded", body);
        }
        if (statusCode >= 400) {
            throw new PolymarketOrderException("HTTP " + statusCode + " error", body);
        }
    }
}
