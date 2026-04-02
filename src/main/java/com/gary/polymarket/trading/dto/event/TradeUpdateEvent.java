package com.gary.polymarket.trading.dto.event;

import com.alibaba.fastjson2.annotation.JSONField;
import com.gary.polymarket.trading.dto.response.MakerOrder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TradeUpdateEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String status;
    private String market;

    @JSONField(name = "asset_id")
    private String assetId;

    private String side;
    private String size;
    private String price;

    @JSONField(name = "transaction_hash")
    private String transactionHash;

    @JSONField(name = "maker_orders")
    private List<MakerOrder> makerOrders;
}
