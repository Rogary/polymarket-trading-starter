package com.gary.polymarket.trading.auth;

import com.gary.polymarket.trading.exception.PolymarketAuthException;
import okhttp3.Request;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Handles L2 authentication (HMAC-SHA256) for every REST request
 * to the Polymarket CLOB API.
 */
public class L2Authenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final L1Authenticator l1Authenticator;
    private final String walletAddress;

    public L2Authenticator(L1Authenticator l1Authenticator, String walletAddress) {
        this.l1Authenticator = l1Authenticator;
        this.walletAddress = walletAddress;
    }

    /**
     * Signs a request by adding Polymarket L2 authentication headers.
     *
     * @param builder     the OkHttp request builder to add headers to
     * @param method      HTTP method (GET, POST, DELETE, etc.)
     * @param requestPath the request path including query string (e.g. "/orders?market=123")
     * @param body        the request body string, or null/empty if none
     * @return the builder with authentication headers added
     */
    public Request.Builder sign(Request.Builder builder, String method, String requestPath, String body) {
        try {
            ApiCredentials credentials = l1Authenticator.getCredentials();

            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String message = timestamp + method.toUpperCase() + requestPath + (body != null ? body : "");

            byte[] decodedSecret = Base64.getDecoder().decode(credentials.getSecret());
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(decodedSecret, HMAC_ALGORITHM));
            byte[] hmacResult = mac.doFinal(message.getBytes());
            String signature = Base64.getEncoder().encodeToString(hmacResult);

            builder.header("POLY_ADDRESS", walletAddress)
                    .header("POLY_SIGNATURE", signature)
                    .header("POLY_TIMESTAMP", timestamp)
                    .header("POLY_API_KEY", credentials.getApiKey())
                    .header("POLY_PASSPHRASE", credentials.getPassphrase());

            return builder;
        } catch (PolymarketAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new PolymarketAuthException("Failed to sign request", e);
        }
    }
}
