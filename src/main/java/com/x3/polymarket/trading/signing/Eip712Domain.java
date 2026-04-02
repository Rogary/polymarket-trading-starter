package com.x3.polymarket.trading.signing;

import java.math.BigInteger;

/**
 * Utility class that builds EIP-712 typed data JSON strings
 * compatible with Web3j's {@link org.web3j.crypto.StructuredDataEncoder}.
 */
public final class Eip712Domain {

    private Eip712Domain() {}

    private static final String ORDER_TYPES =
            "\"EIP712Domain\":["
                    + "{\"name\":\"name\",\"type\":\"string\"},"
                    + "{\"name\":\"version\",\"type\":\"string\"},"
                    + "{\"name\":\"chainId\",\"type\":\"uint256\"},"
                    + "{\"name\":\"verifyingContract\",\"type\":\"address\"}"
                    + "],"
                    + "\"Order\":["
                    + "{\"name\":\"salt\",\"type\":\"uint256\"},"
                    + "{\"name\":\"maker\",\"type\":\"address\"},"
                    + "{\"name\":\"signer\",\"type\":\"address\"},"
                    + "{\"name\":\"taker\",\"type\":\"address\"},"
                    + "{\"name\":\"tokenId\",\"type\":\"uint256\"},"
                    + "{\"name\":\"makerAmount\",\"type\":\"uint256\"},"
                    + "{\"name\":\"takerAmount\",\"type\":\"uint256\"},"
                    + "{\"name\":\"expiration\",\"type\":\"uint256\"},"
                    + "{\"name\":\"nonce\",\"type\":\"uint256\"},"
                    + "{\"name\":\"feeRateBps\",\"type\":\"uint256\"},"
                    + "{\"name\":\"side\",\"type\":\"uint8\"},"
                    + "{\"name\":\"signatureType\",\"type\":\"uint8\"}"
                    + "]";

    private static final String CLOB_AUTH_TYPES =
            "\"EIP712Domain\":["
                    + "{\"name\":\"name\",\"type\":\"string\"},"
                    + "{\"name\":\"version\",\"type\":\"string\"},"
                    + "{\"name\":\"chainId\",\"type\":\"uint256\"}"
                    + "],"
                    + "\"ClobAuth\":["
                    + "{\"name\":\"address\",\"type\":\"address\"},"
                    + "{\"name\":\"timestamp\",\"type\":\"string\"},"
                    + "{\"name\":\"nonce\",\"type\":\"uint256\"},"
                    + "{\"name\":\"message\",\"type\":\"string\"}"
                    + "]";

    /**
     * Builds an EIP-712 JSON string for order signing.
     *
     * @param salt            random salt
     * @param maker           maker address
     * @param signer          signer address
     * @param taker           taker address (typically zero address)
     * @param tokenId         condition token ID
     * @param makerAmount     maker amount in raw units
     * @param takerAmount     taker amount in raw units
     * @param expiration      order expiration timestamp
     * @param nonce           order nonce
     * @param feeRateBps      fee rate in basis points
     * @param side            side as uint8 (0=BUY, 1=SELL)
     * @param signatureType   signature type as uint8
     * @param chainId         chain ID (e.g. 137 for Polygon)
     * @param verifyingContract exchange contract address
     * @return JSON string suitable for StructuredDataEncoder
     */
    public static String buildOrderTypedDataJson(
            long salt,
            String maker,
            String signer,
            String taker,
            String tokenId,
            BigInteger makerAmount,
            BigInteger takerAmount,
            long expiration,
            long nonce,
            String feeRateBps,
            int side,
            int signatureType,
            int chainId,
            String verifyingContract) {

        return "{"
                + "\"types\":{" + ORDER_TYPES + "},"
                + "\"primaryType\":\"Order\","
                + "\"domain\":{"
                + "\"name\":\"ClobExchange\","
                + "\"version\":\"1\","
                + "\"chainId\":\"" + chainId + "\","
                + "\"verifyingContract\":\"" + verifyingContract + "\""
                + "},"
                + "\"message\":{"
                + "\"salt\":\"" + salt + "\","
                + "\"maker\":\"" + maker + "\","
                + "\"signer\":\"" + signer + "\","
                + "\"taker\":\"" + taker + "\","
                + "\"tokenId\":\"" + tokenId + "\","
                + "\"makerAmount\":\"" + makerAmount.toString() + "\","
                + "\"takerAmount\":\"" + takerAmount.toString() + "\","
                + "\"expiration\":\"" + expiration + "\","
                + "\"nonce\":\"" + nonce + "\","
                + "\"feeRateBps\":\"" + feeRateBps + "\","
                + "\"side\":\"" + side + "\","
                + "\"signatureType\":\"" + signatureType + "\""
                + "}"
                + "}";
    }

    /**
     * Builds an EIP-712 JSON string for ClobAuth (L1 authentication) signing.
     *
     * @param address   the signer's Ethereum address
     * @param timestamp ISO timestamp string
     * @param nonce     auth nonce
     * @param message   auth message
     * @param chainId   chain ID (e.g. 137 for Polygon)
     * @return JSON string suitable for StructuredDataEncoder
     */
    public static String buildClobAuthTypedDataJson(
            String address,
            String timestamp,
            int nonce,
            String message,
            int chainId) {

        return "{"
                + "\"types\":{" + CLOB_AUTH_TYPES + "},"
                + "\"primaryType\":\"ClobAuth\","
                + "\"domain\":{"
                + "\"name\":\"ClobAuthDomain\","
                + "\"version\":\"1\","
                + "\"chainId\":\"" + chainId + "\""
                + "},"
                + "\"message\":{"
                + "\"address\":\"" + address + "\","
                + "\"timestamp\":\"" + timestamp + "\","
                + "\"nonce\":\"" + nonce + "\","
                + "\"message\":\"" + message + "\""
                + "}"
                + "}";
    }
}
