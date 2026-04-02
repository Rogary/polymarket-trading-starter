package com.gary.polymarket.trading.dto.event;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

@Data
public class MarketResolvedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String market;

    @JSONField(name = "winning_asset_id")
    private String winningAssetId;
}
