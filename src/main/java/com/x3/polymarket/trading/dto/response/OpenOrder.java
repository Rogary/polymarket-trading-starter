package com.x3.polymarket.trading.dto.response;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OpenOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String status;
    private String owner;

    @JSONField(name = "maker_address")
    private String makerAddress;

    private String market;

    @JSONField(name = "asset_id")
    private String assetId;

    private String side;

    @JSONField(name = "original_size")
    private String originalSize;

    @JSONField(name = "size_matched")
    private String sizeMatched;

    private String price;
    private String outcome;

    @JSONField(name = "order_type")
    private String orderType;

    private String expiration;

    @JSONField(name = "associate_trades")
    private List<String> associateTrades;

    @JSONField(name = "created_at")
    private String createdAt;
}
