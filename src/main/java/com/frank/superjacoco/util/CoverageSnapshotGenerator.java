package com.frank.superjacoco.util;

import com.frank.superjacoco.entity.CoverageSnapshotEntity;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.Map;

public class CoverageSnapshotGenerator {

    public CoverageSnapshotEntity generateFromXml(InputStream xmlStream, String commitId, Map<String, Map<Integer, String>> sourceLines) {
        CoverageSnapshotEntity snapshot = new CoverageSnapshotEntity();
        snapshot.getMetadata().setCommitId(commitId);
        snapshot.getMetadata().setGeneratedAt(String.valueOf(System.currentTimeMillis()));
        snapshot.getMetadata().setGeneratorVersion("super-jacoco-cumulative-coverage");
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            // JaCoCo XML may contain a DOCTYPE that points to report.dtd.
            // Disable external entity loading to avoid file/network dependency.
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.newSAXParser().parse(xmlStream, new DefaultHandler() {
                private String currentPackage;
                private String currentSourceFile;
                private String currentClass;
                private String currentMethodName;
                private String currentMethodDesc;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    if ("package".equals(qName)) {
                        currentPackage = attributes.getValue("name");
                        return;
                    }
                    if ("class".equals(qName)) {
                        currentClass = attributes.getValue("name");
                        return;
                    }
                    if ("sourcefile".equals(qName)) {
                        currentSourceFile = attributes.getValue("name");
                        return;
                    }
                    if ("method".equals(qName)) {
                        currentMethodName = attributes.getValue("name");
                        currentMethodDesc = attributes.getValue("desc");
                        return;
                    }
                    if ("counter".equals(qName) && currentClass != null && currentMethodName != null) {
                        String type = attributes.getValue("type");
                        if (!"LINE".equals(type) && !"BRANCH".equals(type)) {
                            return;
                        }
                        String methodKey = currentClass.replace('/', '.') + "#" + currentMethodName + currentMethodDesc;
                        CoverageSnapshotEntity.MethodCoverage methodCoverage =
                                snapshot.getMethods().computeIfAbsent(methodKey, k -> new CoverageSnapshotEntity.MethodCoverage());
                        methodCoverage.setClassKey(currentClass.replace('/', '.'));
                        methodCoverage.setMethodName(currentMethodName);
                        methodCoverage.setMethodDesc(currentMethodDesc);
                        int missed = Integer.parseInt(attributes.getValue("missed"));
                        int covered = Integer.parseInt(attributes.getValue("covered"));
                        if ("LINE".equals(type)) {
                            methodCoverage.setLineMissed(missed);
                            methodCoverage.setLineCovered(covered);
                        } else {
                            methodCoverage.setBranchMissed(missed);
                            methodCoverage.setBranchCovered(covered);
                        }
                        return;
                    }
                    if (!"line".equals(qName) || currentPackage == null || currentSourceFile == null) {
                        return;
                    }
                    int lineNo = Integer.parseInt(attributes.getValue("nr"));
                    int covered = Integer.parseInt(attributes.getValue("ci"));
                    int branchMissed = parseInt(attributes.getValue("mb"));
                    int branchCovered = parseInt(attributes.getValue("cb"));
                    String filePath = currentPackage + "/" + currentSourceFile;

                    CoverageSnapshotEntity.FileCoverage fileCoverage = snapshot.getFiles()
                            .computeIfAbsent(filePath, k -> new CoverageSnapshotEntity.FileCoverage());
                    CoverageSnapshotEntity.LineCoverage lineCoverage = new CoverageSnapshotEntity.LineCoverage();
                    lineCoverage.setStatus(covered > 0 ? "COVERED" : "MISSED");
                    lineCoverage.setHits(covered);
                    lineCoverage.setBranchMissed(branchMissed);
                    lineCoverage.setBranchCovered(branchCovered);

                    String sourceLine = resolveSourceLine(sourceLines, filePath, lineNo);
                    lineCoverage.setHash(CoverageLineHashUtil.hash(sourceLine));
                    fileCoverage.getLines().put(String.valueOf(lineNo), lineCoverage);
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    if ("method".equals(qName)) {
                        currentMethodName = null;
                        currentMethodDesc = null;
                    }
                    if ("class".equals(qName)) {
                        currentClass = null;
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse jacoco.xml for coverage snapshot.", e);
        }
        return snapshot;
    }

    public CoverageSnapshotEntity merge(CoverageSnapshotEntity base, CoverageSnapshotEntity delta) {
        CoverageSnapshotEntity merged = new CoverageSnapshotEntity();
        merged.setMetadata(delta.getMetadata());
        merged.setSummary(delta.getSummary());
        merged.getFiles().putAll(base.getFiles());

        for (Map.Entry<String, CoverageSnapshotEntity.FileCoverage> fileEntry : delta.getFiles().entrySet()) {
            String filePath = fileEntry.getKey();
            CoverageSnapshotEntity.FileCoverage deltaFile = fileEntry.getValue();
            CoverageSnapshotEntity.FileCoverage mergedFile = merged.getFiles()
                    .computeIfAbsent(filePath, k -> new CoverageSnapshotEntity.FileCoverage());
            for (Map.Entry<String, CoverageSnapshotEntity.LineCoverage> lineEntry : deltaFile.getLines().entrySet()) {
                String lineNo = lineEntry.getKey();
                CoverageSnapshotEntity.LineCoverage deltaLine = lineEntry.getValue();
                CoverageSnapshotEntity.LineCoverage baseLine = mergedFile.getLines().get(lineNo);
                if (baseLine == null) {
                    mergedFile.getLines().put(lineNo, deltaLine);
                    continue;
                }
                boolean covered = "COVERED".equals(baseLine.getStatus()) || "COVERED".equals(deltaLine.getStatus());
                baseLine.setStatus(covered ? "COVERED" : "MISSED");
                baseLine.setHits(Math.max(nullSafe(baseLine.getHits()), nullSafe(deltaLine.getHits())));
                int totalBranches = Math.max(totalBranches(baseLine), totalBranches(deltaLine));
                int coveredBranches = Math.max(nullSafe(baseLine.getBranchCovered()), nullSafe(deltaLine.getBranchCovered()));
                if (coveredBranches > totalBranches) {
                    coveredBranches = totalBranches;
                }
                baseLine.setBranchCovered(coveredBranches);
                baseLine.setBranchMissed(Math.max(totalBranches - coveredBranches, 0));
                if (baseLine.getHash() == null || baseLine.getHash().isEmpty()) {
                    baseLine.setHash(deltaLine.getHash());
                }
            }
        }
        return merged;
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private int totalBranches(CoverageSnapshotEntity.LineCoverage lineCoverage) {
        if (lineCoverage == null) {
            return 0;
        }
        return nullSafe(lineCoverage.getBranchCovered()) + nullSafe(lineCoverage.getBranchMissed());
    }

    private int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private String resolveSourceLine(Map<String, Map<Integer, String>> sourceLines, String filePath, int lineNo) {
        if (sourceLines == null) {
            return "";
        }
        Map<Integer, String> lines = sourceLines.get(filePath);
        if (lines == null) {
            return "";
        }
        return lines.getOrDefault(lineNo, "");
    }
}
