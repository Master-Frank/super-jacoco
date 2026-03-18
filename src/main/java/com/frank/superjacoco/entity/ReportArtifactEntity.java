package com.frank.superjacoco.entity;

import lombok.Data;

import java.util.Date;

@Data
public class ReportArtifactEntity {

    private Long id;
    private String coverageSetId;
    private String runId;
    private String commitId;
    private String artifactType;
    private String objectKey;
    private String metadata;
    private Date createdAt;
}
