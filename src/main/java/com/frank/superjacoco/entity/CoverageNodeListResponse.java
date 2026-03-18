package com.frank.superjacoco.entity;

import lombok.Data;

import java.util.List;

@Data
public class CoverageNodeListResponse {
    private Integer total;
    private Integer page;
    private Integer pageSize;
    private List<CoverageNodeStatsEntity> items;
}
