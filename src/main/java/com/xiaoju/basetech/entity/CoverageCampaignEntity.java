package com.xiaoju.basetech.entity;

import lombok.Data;

import java.util.Date;

@Data
public class CoverageCampaignEntity {

    private String campaignId;
    private String campaignName;
    private String gitUrl;
    private String branch;
    private String metricScope;
    private String fromType;
    private String description;
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
