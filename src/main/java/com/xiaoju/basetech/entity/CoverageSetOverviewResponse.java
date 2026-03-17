package com.xiaoju.basetech.entity;

import lombok.Data;

@Data
public class CoverageSetOverviewResponse {
    private String coverageSetId;
    private String gitUrl;
    private String branch;
    private String currentCommitId;
    private String reportUrl;
    private Double lineCoverageRate;
    private Double branchCoverageRate;
    private Integer totalRuns;
    private String lastUpdated;
}
