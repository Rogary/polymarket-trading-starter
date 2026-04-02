package com.gary.polymarket.trading.dto.event;

import lombok.Data;

import java.io.Serializable;

@Data
public class PriceLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    private String price;
    private String size;
}
