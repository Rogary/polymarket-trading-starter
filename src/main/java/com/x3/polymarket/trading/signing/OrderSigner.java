package com.x3.polymarket.trading.signing;

import com.x3.polymarket.trading.PolymarketTradingProperties;
import com.x3.polymarket.trading.dto.request.PlaceOrderRequest;
import com.x3.polymarket.trading.dto.response.SignedOrder;
import com.x3.polymarket.trading.enums.Side;
import com.x3.polymarket.trading.enums.SignatureType;
import com.x3.polymarket.trading.exception.PolymarketSigningException;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;

/**
 * Signs Polymarket CLOB orders and ClobAuth messages using EIP-712 typed data.
 */
public class OrderSigner {

    private static final BigDecimal SCALE_FACTOR = new BigDecimal("1000000");
    private static final long DEFAULT_NONCE = 0L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PolymarketTradingProperties properties;
    private final ECKeyPair keyPair;
    private final String makerAddress;
    private final String signerAddress;

    public OrderSigner(PolymarketTradingProperties properties) {
        this.properties = properties;

        String hexKey = properties.getPrivateKey();
        if (hexKey == null || hexKey.isEmpty()) {
            throw new PolymarketSigningException("Private key must be configured");
        }
        if (hexKey.startsWith("0x") || hexKey.startsWith("0X")) {
            hexKey = hexKey.substring(2);
        }

        try {
            this.keyPair = ECKeyPair.create(new BigInteger(hexKey, 16));
        } catch (Exception e) {
            throw new PolymarketSigningException("Invalid private key", e);
        }

        String derivedAddress = "0x" + Keys.getAddress(keyPair);
        this.signerAddress = derivedAddress;

        SignatureType sigType = properties.getSignatureType();
        if (sigType == SignatureType.POLY_PROXY || sigType == SignatureType.GNOSIS_SAFE) {
            String funder = properties.getFunderAddress();
            if (funder == null || funder.isEmpty()) {
                throw new PolymarketSigningException(
                        "Funder address is required for signature type " + sigType);
            }
            this.makerAddress = funder;
        } else {
            this.makerAddress = derivedAddress;
        }
    }

    public String getMakerAddress() {
        return makerAddress;
    }

    public String getSignerAddress() {
        return signerAddress;
    }

    /**
     * Builds and signs a Polymarket order from the given request parameters.
     *
     * @param request     the place order request
     * @param feeRateBps  fee rate in basis points (e.g. "0" or "100")
     * @return a fully populated and signed order
     */
    public SignedOrder buildAndSign(PlaceOrderRequest request, String feeRateBps) {
        try {
            BigDecimal price = request.getPrice();
            BigDecimal size = request.getSize();
            Side side = request.getSide();

            BigInteger makerAmount;
            BigInteger takerAmount;
            if (side == Side.BUY) {
                // BUY: makerAmount = price * size * 1e6 (USDC), takerAmount = size * 1e6 (shares)
                makerAmount = price.multiply(size).multiply(SCALE_FACTOR)
                        .setScale(0, RoundingMode.FLOOR).toBigInteger();
                takerAmount = size.multiply(SCALE_FACTOR)
                        .setScale(0, RoundingMode.FLOOR).toBigInteger();
            } else {
                // SELL: makerAmount = size * 1e6 (shares), takerAmount = price * size * 1e6 (USDC)
                makerAmount = size.multiply(SCALE_FACTOR)
                        .setScale(0, RoundingMode.FLOOR).toBigInteger();
                takerAmount = price.multiply(size).multiply(SCALE_FACTOR)
                        .setScale(0, RoundingMode.FLOOR).toBigInteger();
            }

            long salt = Math.abs(SECURE_RANDOM.nextLong());
            long expiration = request.getExpiration() != null ? request.getExpiration() : 0L;
            int sideValue = side == Side.BUY ? 0 : 1;
            int sigTypeValue = properties.getSignatureType().getValue();

            String verifyingContract = request.isNegRisk()
                    ? ContractAddresses.NEG_RISK_CTF_EXCHANGE
                    : ContractAddresses.CTF_EXCHANGE;

            String typedDataJson = Eip712Domain.buildOrderTypedDataJson(
                    salt,
                    makerAddress,
                    signerAddress,
                    ContractAddresses.ZERO_ADDRESS,
                    request.getTokenId(),
                    makerAmount,
                    takerAmount,
                    expiration,
                    DEFAULT_NONCE,
                    feeRateBps,
                    sideValue,
                    sigTypeValue,
                    properties.getChainId(),
                    verifyingContract);

            String signature = signTypedData(typedDataJson);

            return new SignedOrder()
                    .setSalt(salt)
                    .setMaker(makerAddress)
                    .setSigner(signerAddress)
                    .setTaker(ContractAddresses.ZERO_ADDRESS)
                    .setTokenId(request.getTokenId())
                    .setMakerAmount(makerAmount.toString())
                    .setTakerAmount(takerAmount.toString())
                    .setSide(String.valueOf(sideValue))
                    .setExpiration(String.valueOf(expiration))
                    .setNonce(String.valueOf(DEFAULT_NONCE))
                    .setFeeRateBps(feeRateBps)
                    .setSignatureType(sigTypeValue)
                    .setSignature(signature);
        } catch (PolymarketSigningException e) {
            throw e;
        } catch (Exception e) {
            throw new PolymarketSigningException("Failed to build and sign order", e);
        }
    }

    /**
     * Signs a ClobAuth message for L1 authentication.
     *
     * @param timestamp ISO timestamp string
     * @param nonce     auth nonce
     * @return hex-encoded EIP-712 signature
     */
    public String signClobAuthMessage(String timestamp, int nonce) {
        try {
            String typedDataJson = Eip712Domain.buildClobAuthTypedDataJson(
                    signerAddress,
                    timestamp,
                    nonce,
                    "This message attests that I control the given wallet",
                    properties.getChainId());

            return signTypedData(typedDataJson);
        } catch (PolymarketSigningException e) {
            throw e;
        } catch (Exception e) {
            throw new PolymarketSigningException("Failed to sign ClobAuth message", e);
        }
    }

    /**
     * Hashes EIP-712 typed data JSON and signs it with the ECKeyPair.
     * Returns a 65-byte hex signature (r + s + v).
     */
    private String signTypedData(String typedDataJson) {
        try {
            StructuredDataEncoder encoder = new StructuredDataEncoder(typedDataJson);
            byte[] hash = encoder.hashStructuredData();

            // false = do not add Ethereum message prefix
            Sign.SignatureData sigData = Sign.signMessage(hash, keyPair, false);

            byte[] r = sigData.getR();
            byte[] s = sigData.getS();
            byte[] v = sigData.getV();

            // Combine: r(32 bytes) + s(32 bytes) + v(1 byte) = 65 bytes
            byte[] signature = new byte[65];
            System.arraycopy(r, 0, signature, 0, 32);
            System.arraycopy(s, 0, signature, 32, 32);
            System.arraycopy(v, 0, signature, 64, 1);

            return Numeric.toHexString(signature);
        } catch (Exception e) {
            throw new PolymarketSigningException("EIP-712 signing failed", e);
        }
    }
}
