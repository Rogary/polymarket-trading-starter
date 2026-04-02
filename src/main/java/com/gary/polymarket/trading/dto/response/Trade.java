package com.gary.polymarket.trading.dto.response;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class Trade implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    @JSONField(name = "taker_order_id")
    private String takerOrderId;

    private String market;

    @JSONField(name = "asset_id")
    private String assetId;

    private String side;
    private String size;
    private String price;

    @JSONField(name = "fee_rate_bps")
    private String feeRateBps;

    private String status;

    @JSONField(name = "match_time")
    private String matchTime;

    @JSONField(name = "last_update")
    private String lastUpdate;

    private String outcome;
    private String owner;

    @JSONField(name = "maker_address")
    private String makerAddress;

    @JSONField(name = "trader_side")
    private String traderSide;

    @JSONField(name = "transaction_hash")
    private String transactionHash;

    @JSONField(name = "maker_orders")
    private List<MakerOrder> makerOrders;
}
