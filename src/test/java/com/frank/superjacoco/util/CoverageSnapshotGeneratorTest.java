package com.frank.superjacoco.util;

import com.frank.superjacoco.entity.CoverageSnapshotEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class CoverageSnapshotGeneratorTest {

    @Test
    void shouldGenerateAndMergeSnapshot() {
        String xml = "<report><package name=\"com/example\"><sourcefile name=\"Foo.java\">"
                + "<line nr=\"10\" mi=\"0\" ci=\"1\" mb=\"0\" cb=\"2\"/>"
                + "<line nr=\"11\" mi=\"1\" ci=\"0\" mb=\"2\" cb=\"0\"/>"
                + "</sourcefile></package></report>";

        Map<String, Map<Integer, String>> source = new HashMap<>();
        source.put("com/example/Foo.java", new HashMap<>());
        source.get("com/example/Foo.java").put(10, "a();");
        source.get("com/example/Foo.java").put(11, "b();");

        CoverageSnapshotGenerator generator = new CoverageSnapshotGenerator();
        CoverageSnapshotEntity first = generator.generateFromXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                "c1",
                source
        );
        Assertions.assertEquals("COVERED", first.getFiles().get("com/example/Foo.java").getLines().get("10").getStatus());
        Assertions.assertEquals("MISSED", first.getFiles().get("com/example/Foo.java").getLines().get("11").getStatus());
        Assertions.assertFalse(first.getFiles().get("com/example/Foo.java").getLines().get("10").getHash().isEmpty());
        Assertions.assertEquals(2, first.getFiles().get("com/example/Foo.java").getLines().get("10").getBranchCovered());
        Assertions.assertEquals(2, first.getFiles().get("com/example/Foo.java").getLines().get("11").getBranchMissed());

        CoverageSnapshotEntity delta = new CoverageSnapshotEntity();
        CoverageSnapshotEntity.FileCoverage fileCoverage = new CoverageSnapshotEntity.FileCoverage();
        CoverageSnapshotEntity.LineCoverage lineCoverage = new CoverageSnapshotEntity.LineCoverage();
        lineCoverage.setStatus("COVERED");
        lineCoverage.setHits(2);
        lineCoverage.setHash(CoverageLineHashUtil.hash("b();"));
        lineCoverage.setBranchCovered(1);
        lineCoverage.setBranchMissed(1);
        fileCoverage.getLines().put("11", lineCoverage);
        delta.getFiles().put("com/example/Foo.java", fileCoverage);

        CoverageSnapshotEntity merged = generator.merge(first, delta);
        Assertions.assertEquals("COVERED", merged.getFiles().get("com/example/Foo.java").getLines().get("11").getStatus());
        Assertions.assertEquals(1, merged.getFiles().get("com/example/Foo.java").getLines().get("11").getBranchCovered());
        Assertions.assertEquals(1, merged.getFiles().get("com/example/Foo.java").getLines().get("11").getBranchMissed());
    }

    @Test
    void codecShouldRoundTrip() {
        CoverageSnapshotEntity snapshot = new CoverageSnapshotEntity();
        snapshot.getMetadata().setCommitId("abc");
        byte[] bytes = CoverageSnapshotCodec.toGzipBytes(snapshot);
        CoverageSnapshotEntity parsed = CoverageSnapshotCodec.fromGzipBytes(bytes);
        Assertions.assertEquals("abc", parsed.getMetadata().getCommitId());
    }

    @Test
    void shouldParseJacocoXmlWithDoctypeWithoutLoadingExternalDtd() {
        String xml = "<!DOCTYPE report SYSTEM \"report.dtd\">"
                + "<report><package name=\"com/example\"><sourcefile name=\"Foo.java\">"
                + "<line nr=\"1\" mi=\"0\" ci=\"1\" mb=\"0\" cb=\"1\"/>"
                + "</sourcefile></package></report>";

        CoverageSnapshotGenerator generator = new CoverageSnapshotGenerator();
        CoverageSnapshotEntity snapshot = generator.generateFromXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                "c-doctype",
                Collections.emptyMap()
        );
        Assertions.assertEquals(
                "COVERED",
                snapshot.getFiles().get("com/example/Foo.java").getLines().get("1").getStatus()
        );
        Assertions.assertEquals(
                1,
                snapshot.getFiles().get("com/example/Foo.java").getLines().get("1").getBranchCovered()
        );
    }
}
