package com.xiaoju.basetech.entity;

import lombok.Data;

@Data
public class CoverageSourceResponse {
    private String classKey;
    private String sourceFile;
    private String etag;
    private CoverageSnapshotEntity.FileCoverage fileCoverage;
}
