package com.gary.polymarket.trading.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiCredentials {
    private String apiKey;
    private String secret;
    private String passphrase;

    public boolean isPresent() {
        return apiKey != null && !apiKey.isEmpty()
                && secret != null && !secret.isEmpty()
                && passphrase != null && !passphrase.isEmpty();
    }
}
