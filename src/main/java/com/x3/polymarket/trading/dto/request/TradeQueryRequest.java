package com.x3.polymarket.trading.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class TradeQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String market;
    private String assetId;
    private Long before;
    private Long after;
    private String nextCursor;
}
