package com.gary.polymarket.trading.dto.response;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> data;

    @JSONField(name = "next_cursor")
    private String nextCursor;
}
