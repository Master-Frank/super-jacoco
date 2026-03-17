package com.xiaoju.basetech.util;

public class CoverageStorageKeyBuilder {

    private static final String RUNS_DIR = "runs";
    private static final String SNAPSHOTS_DIR = "snapshots";

    private CoverageStorageKeyBuilder() {
    }

    public static String runPrefix(String coverageSetId, String runId) {
        return coverageSetId + "/" + RUNS_DIR + "/" + runId;
    }

    public static String runXmlObjectKey(String coverageSetId, String runId) {
        return runPrefix(coverageSetId, runId) + "/jacoco.xml.gz";
    }

    public static String runExecObjectKey(String coverageSetId, String runId) {
        return runPrefix(coverageSetId, runId) + "/jacoco.exec.gz";
    }

    public static String snapshotPrefix(String coverageSetId, String commitId) {
        return coverageSetId + "/" + SNAPSHOTS_DIR + "/" + commitId;
    }

    public static String snapshotObjectKey(String coverageSetId, String commitId) {
        return snapshotPrefix(coverageSetId, commitId) + "/coverage_snapshot.json.gz";
    }

    public static String snapshotStatsObjectKey(String coverageSetId, String commitId) {
        return snapshotPrefix(coverageSetId, commitId) + "/coverage_stats.json";
    }
}
