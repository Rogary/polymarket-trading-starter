package com.x3.polymarket.trading.dto.event;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

@Data
public class NewMarketEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String market;

    @JSONField(name = "asset_id")
    private String assetId;
}
