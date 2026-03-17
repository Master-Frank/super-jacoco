package com.xiaoju.basetech.entity;

import lombok.Data;

@Data
public class CoverageNodeQuery {
    private String level;
    private String parentKey;
    private String sortBy;
    private String order;
    private Integer page = 1;
    private Integer pageSize = 20;
}
