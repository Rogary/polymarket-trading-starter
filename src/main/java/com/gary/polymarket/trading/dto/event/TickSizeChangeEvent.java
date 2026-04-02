package com.gary.polymarket.trading.dto.event;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

@Data
public class TickSizeChangeEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JSONField(name = "asset_id")
    private String assetId;

    @JSONField(name = "tick_size")
    private String tickSize;
}
