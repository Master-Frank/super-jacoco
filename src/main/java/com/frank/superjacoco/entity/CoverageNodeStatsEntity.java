package com.frank.superjacoco.entity;

import lombok.Data;

import java.util.Date;

@Data
public class CoverageNodeStatsEntity {

    private Long id;
    private String coverageSetId;
    private String commitId;
    private String nodeType;
    private String nodeKey;
    private String parentKey;
    private String displayName;
    private String sourceFile;
    private Integer lineMissed;
    private Integer lineCovered;
    private Integer branchMissed;
    private Integer branchCovered;
    private Integer instructionMissed;
    private Integer instructionCovered;
    private Integer complexityMissed;
    private Integer complexityCovered;
    private Integer methodMissed;
    private Integer methodCovered;
    private Integer classMissed;
    private Integer classCovered;
    private Date updatedAt;
}
