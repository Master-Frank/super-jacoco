package com.frank.superjacoco.entity;

import lombok.Data;

import java.util.Date;

@Data
public class CoverageSetEntity {

    private String coverageSetId;
    private String campaignId;
    private String gitUrl;
    private String repoLocalPath;
    private String branch;
    private String type;
    private String fromType;
    private String scopeKey;
    private String currentCommitId;
    private String currentSnapshotKey;
    private Double lineCoverageRate;
    private Double branchCoverageRate;
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
