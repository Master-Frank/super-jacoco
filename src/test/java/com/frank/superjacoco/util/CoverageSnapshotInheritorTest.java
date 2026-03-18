package com.frank.superjacoco.util;

import com.frank.superjacoco.entity.CoverageSnapshotEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class CoverageSnapshotInheritorTest {

    @Test
    void shouldInheritUnchangedAndMovedLine() {
        CoverageSnapshotEntity oldSnapshot = new CoverageSnapshotEntity();
        CoverageSnapshotEntity.FileCoverage file = new CoverageSnapshotEntity.FileCoverage();

        CoverageSnapshotEntity.LineCoverage line10 = new CoverageSnapshotEntity.LineCoverage();
        line10.setStatus("COVERED");
        line10.setHits(1);
        line10.setHash(CoverageLineHashUtil.hash("A"));
        file.getLines().put("10", line10);

        CoverageSnapshotEntity.LineCoverage line20 = new CoverageSnapshotEntity.LineCoverage();
        line20.setStatus("COVERED");
        line20.setHits(1);
        line20.setHash(CoverageLineHashUtil.hash("MOVE"));
        file.getLines().put("20", line20);

        oldSnapshot.getFiles().put("src/Foo.java", file);

        FileDiff fileDiff = new FileDiff("src/Foo.java", "src/Foo.java");
        fileDiff.addChangedRange(20, 1, 50, 0);
        GitDiffResult diff = new GitDiffResult();
        diff.addFileDiff(fileDiff);

        Map<String, Map<Integer, String>> newSourceLines = new HashMap<>();
        Map<Integer, String> lines = new HashMap<>();
        lines.put(10, "A");
        lines.put(99, "MOVE");
        newSourceLines.put("src/Foo.java", lines);

        CoverageSnapshotInheritor inheritor = new CoverageSnapshotInheritor();
        CoverageSnapshotEntity inherited = inheritor.inherit(oldSnapshot, "new", diff, newSourceLines);

        Assertions.assertNotNull(inherited.getFiles().get("src/Foo.java").getLines().get("10"));
        Assertions.assertNotNull(inherited.getFiles().get("src/Foo.java").getLines().get("99"));
    }

    @Test
    void shouldNotInheritChangedLineForNormalizedJavaPath() {
        CoverageSnapshotEntity oldSnapshot = new CoverageSnapshotEntity();
        CoverageSnapshotEntity.FileCoverage file = new CoverageSnapshotEntity.FileCoverage();

        CoverageSnapshotEntity.LineCoverage changed = new CoverageSnapshotEntity.LineCoverage();
        changed.setStatus("COVERED");
        changed.setHits(1);
        changed.setHash(CoverageLineHashUtil.hash("return Math.addExact(a, b);"));
        file.getLines().put("9", changed);
        oldSnapshot.getFiles().put("com/example/sampletestedservice/service/CalculatorService.java", file);

        FileDiff fileDiff = new FileDiff(
                "sample-tested-service-core/src/main/java/com/example/sampletestedservice/service/CalculatorService.java",
                "sample-tested-service-core/src/main/java/com/example/sampletestedservice/service/CalculatorService.java"
        );
        fileDiff.addChangedRange(9, 1, 9, 2);
        GitDiffResult diff = new GitDiffResult();
        diff.addFileDiff(fileDiff);

        Map<String, Map<Integer, String>> newSourceLines = new HashMap<>();
        Map<Integer, String> lines = new HashMap<>();
        lines.put(9, "int result = Math.addExact(a, b);");
        lines.put(10, "return result;");
        newSourceLines.put("com/example/sampletestedservice/service/CalculatorService.java", lines);

        CoverageSnapshotInheritor inheritor = new CoverageSnapshotInheritor();
        CoverageSnapshotEntity inherited = inheritor.inherit(oldSnapshot, "new", diff, newSourceLines);

        CoverageSnapshotEntity.FileCoverage inheritedFile =
                inherited.getFiles().get("com/example/sampletestedservice/service/CalculatorService.java");
        Assertions.assertTrue(inheritedFile == null || inheritedFile.getLines().isEmpty());
    }
}
