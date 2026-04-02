package com.gary.polymarket.trading.dto.request;

import com.gary.polymarket.trading.enums.OrderType;
import com.gary.polymarket.trading.enums.Side;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class PlaceOrderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tokenId;
    private BigDecimal price;
    private BigDecimal size;
    private Side side;
    private OrderType orderType = OrderType.GTC;
    private Long expiration;
    private String tickSize = "0.01";
    private boolean negRisk;
    private Boolean postOnly;
}
