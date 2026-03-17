package com.xiaoju.basetech.util;

import com.xiaoju.basetech.entity.CoverageSetEntity;
import com.xiaoju.basetech.entity.CoverageSnapshotEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

class CumulativeCoverageHtmlReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteTemplatePagesAndJsonData() throws Exception {
        CoverageSetEntity set = new CoverageSetEntity();
        set.setCoverageSetId("set-1");
        set.setBranch("master");
        set.setLineCoverageRate(0.1D);
        set.setBranchCoverageRate(0.1D);

        CoverageSnapshotEntity snapshot = new CoverageSnapshotEntity();
        snapshot.getMetadata().setCommitId("commit-1");
        CoverageSnapshotEntity.FileCoverage fileCoverage = new CoverageSnapshotEntity.FileCoverage();
        CoverageSnapshotEntity.LineCoverage covered = new CoverageSnapshotEntity.LineCoverage();
        covered.setStatus("COVERED");
        covered.setHits(1);
        covered.setBranchCovered(2);
        covered.setBranchMissed(0);
        fileCoverage.getLines().put("1", covered);
        CoverageSnapshotEntity.LineCoverage missed = new CoverageSnapshotEntity.LineCoverage();
        missed.setStatus("MISSED");
        missed.setHits(0);
        missed.setBranchCovered(1);
        missed.setBranchMissed(1);
        fileCoverage.getLines().put("2", missed);
        snapshot.getFiles().put("com/example/Foo.java", fileCoverage);

        Map<String, Map<Integer, String>> sourceLines = new HashMap<>();
        Map<Integer, String> fileLines = new HashMap<>();
        fileLines.put(1, "first();");
        fileLines.put(2, "second();");
        sourceLines.put("com/example/Foo.java", fileLines);

        CumulativeCoverageHtmlReportWriter writer = new CumulativeCoverageHtmlReportWriter();
        Path index = writer.writeReport(tempDir, set, snapshot, sourceLines);

        Assertions.assertTrue(Files.exists(index));
        Assertions.assertTrue(Files.exists(tempDir.resolve("set-1/cumulative/package.html")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("set-1/cumulative/class.html")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("set-1/cumulative/assets/app.js")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("set-1/cumulative/data/summary.json.gz")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("set-1/cumulative/data/classes/com/example/Foo.json.gz")));

        String indexHtml = Files.readString(index);
        Assertions.assertTrue(indexHtml.contains("data-page=\"index\""));

        String appJs = Files.readString(tempDir.resolve("set-1/cumulative/assets/app.js"));
        Assertions.assertTrue(appJs.contains("\\u8fd4\\u56de\\u4e0a\\u4e00\\u9875"));
        Assertions.assertTrue(appJs.contains("\\u5168\\u90e8' + total + '\\u4e2a\\u5206\\u652f\\u5df2\\u8986\\u76d6"));
        Assertions.assertTrue(appJs.contains("\\u5171' + total + '\\u4e2a\\u5206\\u652f\\uff0c\\u5df2\\u8986\\u76d6"));
        Assertions.assertFalse(appJs.contains("line.branchCovered === total) {\r\n                      return '<span class=\"branch-dot branch-full\">"));

        String summaryJson = readGzipText(tempDir.resolve("set-1/cumulative/data/summary.json.gz"));
        Assertions.assertTrue(summaryJson.contains("com.example"));
        Assertions.assertTrue(summaryJson.contains("Foo.java"));
        Assertions.assertTrue(summaryJson.contains("\"lineCoverageRate\" : 0.5"));
        Assertions.assertTrue(summaryJson.contains("\"branchCoverageRate\" : 0.75"));

        String classJson = readGzipText(tempDir.resolve("set-1/cumulative/data/classes/com/example/Foo.json.gz"));
        Assertions.assertTrue(classJson.contains("first();"));
        Assertions.assertTrue(classJson.contains("\"branchCovered\" : 2"));
        Assertions.assertTrue(classJson.contains("\"branchMissed\" : 1"));
    }

    private String readGzipText(Path path) throws Exception {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(Files.newInputStream(path));
             Reader reader = new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, len);
            }
            return builder.toString();
        }
    }
}
