package com.x3.polymarket.trading.auth;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.x3.polymarket.trading.PolymarketTradingProperties;
import com.x3.polymarket.trading.exception.PolymarketAuthException;
import com.x3.polymarket.trading.signing.OrderSigner;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles L1 authentication (EIP-712 ClobAuth) to derive API credentials
 * from the Polymarket CLOB server.
 */
public class L1Authenticator {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private final PolymarketTradingProperties properties;
    private final OrderSigner orderSigner;
    private final OkHttpClient httpClient;
    private final AtomicReference<ApiCredentials> cachedCredentials = new AtomicReference<>();

    public L1Authenticator(PolymarketTradingProperties properties,
                           OrderSigner orderSigner) {
        this.properties = properties;
        this.orderSigner = orderSigner;
        this.httpClient = new OkHttpClient();

        // If API credentials are already provided in properties, cache them immediately
        if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()
                && properties.getApiSecret() != null && !properties.getApiSecret().isEmpty()
                && properties.getApiPassphrase() != null && !properties.getApiPassphrase().isEmpty()) {
            cachedCredentials.set(new ApiCredentials(
                    properties.getApiKey(),
                    properties.getApiSecret(),
                    properties.getApiPassphrase()));
        }
    }

    /**
     * Returns cached API credentials, or derives new ones via L1 auth if not cached.
     *
     * @return valid API credentials
     */
    public ApiCredentials getCredentials() {
        ApiCredentials credentials = cachedCredentials.get();
        if (credentials != null && credentials.isPresent()) {
            return credentials;
        }
        return deriveCredentials();
    }

    /**
     * Forces re-derivation of API credentials via the CLOB auth endpoint.
     *
     * @return freshly derived API credentials
     */
    public ApiCredentials deriveCredentials() {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String signature = orderSigner.signClobAuthMessage(timestamp, 0);
            String url = properties.getBaseUrl() + "/auth/derive-api-key";

            RequestBody body = RequestBody.create("{}", JSON_MEDIA_TYPE);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .header("POLY_ADDRESS", orderSigner.getMakerAddress())
                    .header("POLY_SIGNATURE", signature)
                    .header("POLY_TIMESTAMP", timestamp)
                    .header("POLY_NONCE", "0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                String responseStr = responseBody != null ? responseBody.string() : "";

                if (!response.isSuccessful()) {
                    throw new PolymarketAuthException(
                            "Failed to derive API key, HTTP " + response.code() + ": " + responseStr);
                }

                JSONObject json = JSON.parseObject(responseStr);
                if (json == null) {
                    throw new PolymarketAuthException(
                            "Failed to parse derive-api-key response: " + responseStr);
                }

                ApiCredentials credentials = new ApiCredentials(
                        json.getString("apiKey"),
                        json.getString("secret"),
                        json.getString("passphrase"));

                if (!credentials.isPresent()) {
                    throw new PolymarketAuthException(
                            "Derived credentials are incomplete: " + responseStr);
                }

                cachedCredentials.set(credentials);
                return credentials;
            }
        } catch (PolymarketAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new PolymarketAuthException("Failed to derive API credentials", e);
        }
    }
}
