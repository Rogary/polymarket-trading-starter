package com.x3.polymarket.trading.dto.event;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

@Data
public class OrderUpdateEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JSONField(name = "id")
    private String orderId;

    private String type;
    private String status;
    private String market;

    @JSONField(name = "asset_id")
    private String assetId;

    private String side;
    private String price;

    @JSONField(name = "original_size")
    private String size;
}
