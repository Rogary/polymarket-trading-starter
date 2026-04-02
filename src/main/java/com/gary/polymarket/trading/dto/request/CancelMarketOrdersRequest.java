package com.gary.polymarket.trading.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class CancelMarketOrdersRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String market;
    private String assetId;
}
