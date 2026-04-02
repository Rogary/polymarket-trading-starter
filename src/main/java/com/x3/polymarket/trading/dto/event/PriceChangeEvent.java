package com.x3.polymarket.trading.dto.event;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

@Data
public class PriceChangeEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JSONField(name = "asset_id")
    private String assetId;

    private String price;
    private String size;
    private String side;
}
