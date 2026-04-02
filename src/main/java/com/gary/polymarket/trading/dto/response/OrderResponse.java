package com.gary.polymarket.trading.dto.response;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OrderResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;

    @JSONField(name = "orderID")
    private String orderId;

    private String status;
    private String makingAmount;
    private String takingAmount;

    @JSONField(name = "transactionsHashes")
    private List<String> transactionHashes;

    @JSONField(name = "tradeIDs")
    private List<String> tradeIds;

    private String errorMsg;
}
