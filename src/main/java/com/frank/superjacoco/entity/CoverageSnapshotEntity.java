package com.frank.superjacoco.entity;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class CoverageSnapshotEntity {

    private Metadata metadata = new Metadata();
    private Summary summary = new Summary();
    private Map<String, FileCoverage> files = new LinkedHashMap<>();
    private Map<String, MethodCoverage> methods = new LinkedHashMap<>();

    @Data
    public static class Metadata {
        private String coverageSetId;
        private String commitId;
        private String gitUrl;
        private String branch;
        private String generatedAt;
        private String generatorVersion;
    }

    @Data
    public static class Summary {
        private Integer totalFiles = 0;
        private Integer totalLines = 0;
        private Integer coveredLines = 0;
        private Integer missedLines = 0;
        private Double lineCoverageRate = 0D;
        private Integer totalBranches = 0;
        private Integer coveredBranches = 0;
        private Double branchCoverageRate = 0D;
    }

    @Data
    public static class FileCoverage {
        private Map<String, LineCoverage> lines = new LinkedHashMap<>();
        private FileSummary summary = new FileSummary();
    }

    @Data
    public static class FileSummary {
        private Integer totalLines = 0;
        private Integer coveredLines = 0;
        private Double lineCoverageRate = 0D;
    }

    @Data
    public static class LineCoverage {
        private String status;
        private Integer hits;
        private String hash;
        private Integer branchMissed = 0;
        private Integer branchCovered = 0;
    }

    @Data
    public static class MethodCoverage {
        private String classKey;
        private String methodName;
        private String methodDesc;
        private Integer lineMissed = 0;
        private Integer lineCovered = 0;
        private Integer branchMissed = 0;
        private Integer branchCovered = 0;
    }
}
