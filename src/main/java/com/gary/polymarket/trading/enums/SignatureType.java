package com.gary.polymarket.trading.enums;

import lombok.Getter;

@Getter
public enum SignatureType {
    EOA(0),
    POLY_PROXY(1),
    GNOSIS_SAFE(2);

    private final int value;

    SignatureType(int value) {
        this.value = value;
    }
}
