package com.x3.polymarket.trading.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class SignedOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private String maker;
    private String signer;
    private String taker;
    private String tokenId;
    private String makerAmount;
    private String takerAmount;
    private String side;
    private String expiration;
    private String nonce;
    private String feeRateBps;
    private String signature;
    private long salt;
    private int signatureType;
}
