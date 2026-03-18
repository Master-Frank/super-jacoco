package com.frank.superjacoco.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CoverageStorageKeyBuilderTest {

    @Test
    void shouldBuildKeysUnderSetIdRoot() {
        String setId = "set-123";
        String runId = "run-456";
        String commitId = "commit-789";

        Assertions.assertEquals(
                "set-123/runs/run-456/jacoco.xml.gz",
                CoverageStorageKeyBuilder.runXmlObjectKey(setId, runId)
        );
        Assertions.assertEquals(
                "set-123/runs/run-456/jacoco.exec.gz",
                CoverageStorageKeyBuilder.runExecObjectKey(setId, runId)
        );
        Assertions.assertEquals(
                "set-123/snapshots/commit-789/coverage_snapshot.json.gz",
                CoverageStorageKeyBuilder.snapshotObjectKey(setId, commitId)
        );
    }
}
