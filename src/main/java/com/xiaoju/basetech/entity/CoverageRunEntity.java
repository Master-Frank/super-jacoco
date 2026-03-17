package com.xiaoju.basetech.entity;

import lombok.Data;

import java.util.Date;

@Data
public class CoverageRunEntity {

    private String runId;
    private String coverageSetId;
    private String commitId;
    private String commitMessage;
    private String execObjectKey;
    private String xmlObjectKey;
    private Double lineCoverageRate;
    private Double branchCoverageRate;
    private String status;
    private String errorMessage;
    private Date createdAt;
    private Date updatedAt;
}
