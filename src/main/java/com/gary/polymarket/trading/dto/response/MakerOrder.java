package com.gary.polymarket.trading.dto.response;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

@Data
public class MakerOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @JSONField(name = "order_id")
    private String orderId;

    @JSONField(name = "matched_amount")
    private String matchedAmount;
}
