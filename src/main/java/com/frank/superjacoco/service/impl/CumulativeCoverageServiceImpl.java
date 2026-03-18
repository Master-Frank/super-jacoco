package com.frank.superjacoco.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.frank.superjacoco.dao.CoverageNodeStatsDao;
import com.frank.superjacoco.dao.CoverageRunDao;
import com.frank.superjacoco.dao.CoverageSetDao;
import com.frank.superjacoco.dao.ReportArtifactDao;
import com.frank.superjacoco.entity.*;
import com.frank.superjacoco.service.CumulativeCoverageService;
import com.frank.superjacoco.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
public class CumulativeCoverageServiceImpl implements CumulativeCoverageService {

    private final CoverageSetDao coverageSetDao;
    private final CoverageRunDao coverageRunDao;
    private final CoverageNodeStatsDao coverageNodeStatsDao;
    private final ReportArtifactDao reportArtifactDao;
    private final LocalObjectStorage objectStorage;
    private final Executor covJobExecutor;

    private final CoverageSnapshotGenerator snapshotGenerator = new CoverageSnapshotGenerator();
    private final CoverageSnapshotInheritor snapshotInheritor = new CoverageSnapshotInheritor();
    private final CumulativeCoverageHtmlReportWriter cumulativeCoverageHtmlReportWriter = new CumulativeCoverageHtmlReportWriter();
    private final GitDiffParser gitDiffParser = new GitDiffParser();
    private final Cache<String, CoverageSnapshotEntity> snapshotCache =
            CacheBuilder.newBuilder().maximumSize(100).expireAfterWrite(10, TimeUnit.MINUTES).build();

    @Autowired
    public CumulativeCoverageServiceImpl(CoverageSetDao coverageSetDao,
                                         CoverageRunDao coverageRunDao,
                                         CoverageNodeStatsDao coverageNodeStatsDao,
                                         ReportArtifactDao reportArtifactDao,
                                         LocalObjectStorage objectStorage,
                                         @Qualifier("covJobExecutor") Executor covJobExecutor) {
        this.coverageSetDao = coverageSetDao;
        this.coverageRunDao = coverageRunDao;
        this.coverageNodeStatsDao = coverageNodeStatsDao;
        this.reportArtifactDao = reportArtifactDao;
        this.objectStorage = objectStorage;
        this.covJobExecutor = covJobExecutor;
    }

    @Override
    public CoverageSetEntity createCoverageSet(CreateCoverageSetRequest request) {
        String scopeKey = buildScopeKey(request.getGitUrl(), request.getBranch(), request.getType(), request.getFromType());
        CoverageSetEntity existing = coverageSetDao.selectByScopeKey(scopeKey);
        if (existing != null) {
            return existing;
        }
        CoverageSetEntity item = new CoverageSetEntity();
        item.setCoverageSetId(StringUtils.hasText(request.getCoverageSetId()) ? request.getCoverageSetId() : UUID.randomUUID().toString());
        item.setCampaignId(request.getCampaignId());
        item.setGitUrl(request.getGitUrl());
        item.setRepoLocalPath(request.getRepoLocalPath());
        item.setBranch(request.getBranch());
        item.setType(request.getType());
        item.setFromType(request.getFromType());
        item.setScopeKey(scopeKey);
        item.setStatus("ACTIVE");
        coverageSetDao.insert(item);
        return coverageSetDao.selectById(item.getCoverageSetId());
    }

    @Override
    public CoverageRunEntity appendCoverageRun(String coverageSetId, AppendCoverageRunRequest request) {
        CoverageSetEntity set = requireSet(coverageSetId);

        String runId = StringUtils.hasText(request.getRunId()) ? request.getRunId() : UUID.randomUUID().toString();
        CoverageRunEntity existed = coverageRunDao.selectById(runId);
        if (existed != null) {
            return existed;
        }
        String xmlObjectKey = prepareXmlObject(coverageSetId, runId, request.getXmlObjectKey());
        String execObjectKey = prepareExecObject(coverageSetId, runId, request.getExecObjectKey());

        CoverageRunEntity run = new CoverageRunEntity();
        run.setRunId(runId);
        run.setCoverageSetId(set.getCoverageSetId());
        run.setCommitId(request.getCommitId());
        run.setCommitMessage(request.getCommitMessage());
        run.setXmlObjectKey(xmlObjectKey);
        run.setExecObjectKey(execObjectKey);
        run.setStatus("PENDING");
        coverageRunDao.insert(run);

        ReportArtifactEntity xmlArtifact = new ReportArtifactEntity();
        xmlArtifact.setCoverageSetId(coverageSetId);
        xmlArtifact.setRunId(runId);
        xmlArtifact.setCommitId(request.getCommitId());
        xmlArtifact.setArtifactType("JACOCO_XML_GZ");
        xmlArtifact.setObjectKey(xmlObjectKey);
        reportArtifactDao.insert(xmlArtifact);
        if (StringUtils.hasText(execObjectKey)) {
            ReportArtifactEntity execArtifact = new ReportArtifactEntity();
            execArtifact.setCoverageSetId(coverageSetId);
            execArtifact.setRunId(runId);
            execArtifact.setCommitId(request.getCommitId());
            execArtifact.setArtifactType("JACOCO_EXEC_GZ");
            execArtifact.setObjectKey(execObjectKey);
            reportArtifactDao.insert(execArtifact);
        }

        covJobExecutor.execute(() -> processRun(runId));
        return coverageRunDao.selectById(runId);
    }

    @Override
    public CoverageRunEntity getCoverageRun(String coverageSetId, String runId) {
        requireSet(coverageSetId);
        CoverageRunEntity run = coverageRunDao.selectById(runId);
        if (run == null || !coverageSetId.equals(run.getCoverageSetId())) {
            throw new ResponseException(ErrorCode.FAIL, "run不存在: " + runId);
        }
        return run;
    }

    @Override
    public void refreshCoverageSet(String coverageSetId) {
        CoverageRunEntity latest = coverageRunDao.selectLatestBySetId(coverageSetId);
        if (latest == null) {
            throw new ResponseException(ErrorCode.FAIL, "没有可重算的run");
        }
        covJobExecutor.execute(() -> processRun(latest.getRunId()));
    }

    @Override
    public CoverageSetOverviewResponse getCoverageSetOverview(String coverageSetId) {
        CoverageSetEntity set = requireSet(coverageSetId);
        CoverageSetOverviewResponse response = new CoverageSetOverviewResponse();
        response.setCoverageSetId(set.getCoverageSetId());
        response.setGitUrl(set.getGitUrl());
        response.setBranch(set.getBranch());
        response.setCurrentCommitId(set.getCurrentCommitId());
        response.setReportUrl(buildReportUrl(set));
        response.setLineCoverageRate(set.getLineCoverageRate());
        response.setBranchCoverageRate(set.getBranchCoverageRate());
        response.setTotalRuns(Optional.ofNullable(coverageRunDao.countBySetId(coverageSetId)).orElse(0));
        response.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return response;
    }

    @Override
    public CoverageNodeListResponse queryNodes(String coverageSetId, CoverageNodeQuery query) {
        requireSet(coverageSetId);
        int page = Optional.ofNullable(query.getPage()).orElse(1);
        int pageSize = Optional.ofNullable(query.getPageSize()).orElse(20);
        int offset = Math.max((page - 1) * pageSize, 0);
        String nodeType = StringUtils.hasText(query.getLevel()) ? query.getLevel() : "PACKAGE";

        List<CoverageNodeStatsEntity> rows = coverageNodeStatsDao.queryBySetAndType(
                coverageSetId, nodeType, query.getParentKey(), offset, pageSize
        );
        sortRows(rows, query.getSortBy(), query.getOrder());

        CoverageNodeListResponse response = new CoverageNodeListResponse();
        response.setTotal(Optional.ofNullable(coverageNodeStatsDao.countBySetAndType(coverageSetId, nodeType, query.getParentKey())).orElse(0));
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setItems(rows);
        return response;
    }

    @Override
    public CoverageSourceResponse querySource(String coverageSetId, String classKey) {
        CoverageSetEntity set = requireSet(coverageSetId);
        if (!StringUtils.hasText(set.getCurrentSnapshotKey())) {
            throw new ResponseException(ErrorCode.FAIL, "当前没有可查询快照");
        }
        CoverageSnapshotEntity snapshot = getSnapshotCached(set.getCurrentSnapshotKey());
        String path = classKey.replace('.', '/') + ".java";
        CoverageSnapshotEntity.FileCoverage fileCoverage = null;
        for (Map.Entry<String, CoverageSnapshotEntity.FileCoverage> entry : snapshot.getFiles().entrySet()) {
            if (entry.getKey().endsWith(path)) {
                fileCoverage = entry.getValue();
                path = entry.getKey();
                break;
            }
        }
        if (fileCoverage == null) {
            throw new ResponseException(ErrorCode.FAIL, "未找到类对应的覆盖明细");
        }
        CoverageSourceResponse response = new CoverageSourceResponse();
        response.setClassKey(classKey);
        response.setSourceFile(path);
        response.setEtag(CoverageLineHashUtil.hash(classKey + ":" + set.getCurrentSnapshotKey()));
        response.setFileCoverage(fileCoverage);
        return response;
    }

    private void processRun(String runId) {
        CoverageRunEntity run = coverageRunDao.selectById(runId);
        if (run == null) {
            return;
        }
        coverageRunDao.updateStatus(runId, "PROCESSING", "");
        try {
            CoverageSetEntity set = requireSet(run.getCoverageSetId());
            CoverageSnapshotEntity newSnapshot = parseRunSnapshot(run, set);
            CoverageSnapshotEntity merged;
            if (!StringUtils.hasText(set.getCurrentCommitId()) || !StringUtils.hasText(set.getCurrentSnapshotKey())) {
                merged = newSnapshot;
            } else if (set.getCurrentCommitId().equals(run.getCommitId())) {
                CoverageSnapshotEntity current = getSnapshotCached(set.getCurrentSnapshotKey());
                merged = snapshotGenerator.merge(current, newSnapshot);
            } else {
                CoverageSnapshotEntity oldSnapshot = getSnapshotCached(set.getCurrentSnapshotKey());
                GitDiffResult diffResult = safeDiff(set.getCurrentCommitId(), run.getCommitId(), set.getRepoLocalPath());
                CoverageSnapshotEntity inherited = snapshotInheritor.inherit(
                        oldSnapshot, run.getCommitId(), diffResult, loadSourceLines(set.getRepoLocalPath())
                );
                merged = snapshotGenerator.merge(inherited, newSnapshot);
            }

            String newSnapshotKey = CoverageStorageKeyBuilder.snapshotObjectKey(set.getCoverageSetId(), run.getCommitId());
            objectStorage.putBytes(newSnapshotKey, CoverageSnapshotCodec.toGzipBytes(merged));
            snapshotCache.put(newSnapshotKey, merged);
            cleanupOldSnapshotIfNeeded(set, newSnapshotKey);

            updateSetBySnapshot(set, run, merged, newSnapshotKey);
            rebuildNodeStats(set.getCoverageSetId(), run.getCommitId(), merged);
            writeHtmlReport(set, run, merged);
            coverageRunDao.updateStatus(runId, "COMPLETED", "");
        } catch (Exception e) {
            log.error("process run failed, runId={}", runId, e);
            coverageRunDao.updateStatus(runId, "FAILED", e.getMessage());
        }
    }

    private CoverageSnapshotEntity parseRunSnapshot(CoverageRunEntity run, CoverageSetEntity set) throws Exception {
        byte[] xmlGz = objectStorage.getBytes(run.getXmlObjectKey());
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(xmlGz))) {
            Map<String, Map<Integer, String>> sourceLines = loadSourceLines(set.getRepoLocalPath());
            CoverageSnapshotEntity snapshot = snapshotGenerator.generateFromXml(gzipInputStream, run.getCommitId(), sourceLines);
            snapshot.getMetadata().setCoverageSetId(set.getCoverageSetId());
            snapshot.getMetadata().setGitUrl(set.getGitUrl());
            snapshot.getMetadata().setBranch(set.getBranch());
            return snapshot;
        }
    }

    private GitDiffResult safeDiff(String oldCommit, String newCommit, String repoLocalPath) {
        try {
            if (!StringUtils.hasText(repoLocalPath)) {
                throw new ResponseException(ErrorCode.FAIL, "commit变化但未配置repoLocalPath，无法执行diff");
            }
            Path repoPath = Path.of(repoLocalPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(repoPath.resolve(".git"))) {
                throw new ResponseException(ErrorCode.FAIL, "repoLocalPath不是有效git仓库: " + repoPath);
            }
            return gitDiffParser.diff(oldCommit, newCommit, repoPath.toAbsolutePath().normalize().toString());
        } catch (Exception e) {
            throw new RuntimeException("git diff failed", e);
        }
    }

    private void rebuildNodeStats(String coverageSetId, String commitId, CoverageSnapshotEntity snapshot) {
        coverageNodeStatsDao.deleteBySetId(coverageSetId);
        Map<String, CoverageNodeStatsEntity> packageMap = new HashMap<>();
        Map<String, CoverageNodeStatsEntity> classMap = new HashMap<>();
        Map<String, CoverageNodeStatsEntity> methodMap = new HashMap<>();

        for (Map.Entry<String, CoverageSnapshotEntity.FileCoverage> fileEntry : snapshot.getFiles().entrySet()) {
            String filePath = fileEntry.getKey();
            CoverageSnapshotEntity.FileCoverage fileCoverage = fileEntry.getValue();
            String classKey = normalizeClassKey(filePath);
            String packageKey = classKey.contains(".") ? classKey.substring(0, classKey.lastIndexOf('.')) : "";
            CoverageNodeStatsEntity classNode = classMap.computeIfAbsent(classKey,
                    k -> initNode(coverageSetId, commitId, "CLASS", classKey, packageKey, classKey, filePath));
            CoverageNodeStatsEntity packageNode = packageMap.computeIfAbsent(packageKey,
                    k -> initNode(coverageSetId, commitId, "PACKAGE", packageKey, "", packageKey, null));
            for (CoverageSnapshotEntity.LineCoverage line : fileCoverage.getLines().values()) {
                boolean covered = "COVERED".equals(line.getStatus());
                if (covered) {
                    classNode.setLineCovered(classNode.getLineCovered() + 1);
                    packageNode.setLineCovered(packageNode.getLineCovered() + 1);
                } else {
                    classNode.setLineMissed(classNode.getLineMissed() + 1);
                    packageNode.setLineMissed(packageNode.getLineMissed() + 1);
                }
                classNode.setBranchCovered(classNode.getBranchCovered() + nullSafe(line.getBranchCovered()));
                classNode.setBranchMissed(classNode.getBranchMissed() + nullSafe(line.getBranchMissed()));
                packageNode.setBranchCovered(packageNode.getBranchCovered() + nullSafe(line.getBranchCovered()));
                packageNode.setBranchMissed(packageNode.getBranchMissed() + nullSafe(line.getBranchMissed()));
            }
        }
        for (Map.Entry<String, CoverageSnapshotEntity.MethodCoverage> entry : snapshot.getMethods().entrySet()) {
            String methodKey = entry.getKey();
            CoverageSnapshotEntity.MethodCoverage method = entry.getValue();
            String classKey = method.getClassKey();
            CoverageNodeStatsEntity methodNode = initNode(
                    coverageSetId,
                    commitId,
                    "METHOD",
                    methodKey,
                    classKey,
                    method.getMethodName() + method.getMethodDesc(),
                    null
            );
            methodNode.setLineCovered(nullSafe(method.getLineCovered()));
            methodNode.setLineMissed(nullSafe(method.getLineMissed()));
            methodNode.setBranchCovered(nullSafe(method.getBranchCovered()));
            methodNode.setBranchMissed(nullSafe(method.getBranchMissed()));
            methodMap.put(methodKey, methodNode);
        }

        List<CoverageNodeStatsEntity> all = new ArrayList<>();
        all.addAll(packageMap.values());
        all.addAll(classMap.values());
        all.addAll(methodMap.values());
        if (!all.isEmpty()) {
            coverageNodeStatsDao.batchInsert(all);
        }
    }

    private CoverageNodeStatsEntity initNode(String coverageSetId, String commitId, String nodeType, String nodeKey,
                                             String parentKey, String displayName, String sourceFile) {
        CoverageNodeStatsEntity node = new CoverageNodeStatsEntity();
        node.setCoverageSetId(coverageSetId);
        node.setCommitId(commitId);
        node.setNodeType(nodeType);
        node.setNodeKey(nodeKey);
        node.setParentKey(parentKey == null ? "" : parentKey);
        node.setDisplayName(displayName);
        node.setSourceFile(sourceFile);
        node.setLineCovered(0);
        node.setLineMissed(0);
        node.setBranchCovered(0);
        node.setBranchMissed(0);
        node.setInstructionCovered(0);
        node.setInstructionMissed(0);
        node.setComplexityCovered(0);
        node.setComplexityMissed(0);
        node.setMethodCovered(0);
        node.setMethodMissed(0);
        node.setClassCovered(0);
        node.setClassMissed(0);
        return node;
    }

    private void updateSetBySnapshot(CoverageSetEntity set,
                                     CoverageRunEntity run,
                                     CoverageSnapshotEntity merged,
                                     String snapshotKey) {
        SummaryRate rate = computeRate(merged);
        coverageSetDao.updateCurrentSnapshot(set.getCoverageSetId(), run.getCommitId(), snapshotKey);
        coverageSetDao.updateCoverageRate(set.getCoverageSetId(), rate.lineCoverageRate, rate.branchCoverageRate);
        set.setCurrentCommitId(run.getCommitId());
        set.setCurrentSnapshotKey(snapshotKey);
        set.setLineCoverageRate(rate.lineCoverageRate);
        set.setBranchCoverageRate(rate.branchCoverageRate);
    }

    private void cleanupOldSnapshotIfNeeded(CoverageSetEntity set, String newSnapshotKey) {
        if (StringUtils.hasText(set.getCurrentSnapshotKey()) && !set.getCurrentSnapshotKey().equals(newSnapshotKey)) {
            objectStorage.deleteObject(set.getCurrentSnapshotKey());
            snapshotCache.invalidate(set.getCurrentSnapshotKey());
        }
    }

    private void writeHtmlReport(CoverageSetEntity set,
                                 CoverageRunEntity run,
                                 CoverageSnapshotEntity merged) throws Exception {
        Path reportRoot = objectStorage.resolveObjectPath(set.getCoverageSetId()).getParent();
        Path index = cumulativeCoverageHtmlReportWriter.writeReport(
                reportRoot,
                set,
                merged,
                loadSourceLines(set.getRepoLocalPath())
        );
        ReportArtifactEntity htmlArtifact = new ReportArtifactEntity();
        htmlArtifact.setCoverageSetId(set.getCoverageSetId());
        htmlArtifact.setRunId(run.getRunId());
        htmlArtifact.setCommitId(run.getCommitId());
        htmlArtifact.setArtifactType("CUMULATIVE_HTML_INDEX");
        htmlArtifact.setObjectKey(reportRoot.relativize(index.toAbsolutePath().normalize()).toString().replace('\\', '/'));
        htmlArtifact.setMetadata(buildReportUrl(set));
        reportArtifactDao.insert(htmlArtifact);
    }

    private String buildReportUrl(CoverageSetEntity set) {
        return cumulativeCoverageHtmlReportWriter.buildReportUrl(set.getCoverageSetId());
    }

    private CoverageSetEntity requireSet(String coverageSetId) {
        CoverageSetEntity set = coverageSetDao.selectById(coverageSetId);
        if (set == null) {
            throw new ResponseException(ErrorCode.FAIL, "coverage_set不存在: " + coverageSetId);
        }
        return set;
    }

    private String prepareXmlObject(String setId, String runId, String xmlObjectKeyOrPath) {
        String target = CoverageStorageKeyBuilder.runXmlObjectKey(setId, runId);
        copySourceToObject(xmlObjectKeyOrPath, target);
        return target;
    }

    private String prepareExecObject(String setId, String runId, String execObjectKeyOrPath) {
        if (!StringUtils.hasText(execObjectKeyOrPath)) {
            return null;
        }
        String target = CoverageStorageKeyBuilder.runExecObjectKey(setId, runId);
        copySourceToObject(execObjectKeyOrPath, target);
        return target;
    }

    private void copySourceToObject(String objectKeyOrPath, String targetObjectKey) {
        Path path = null;
        try {
            path = Path.of(objectKeyOrPath).toAbsolutePath().normalize();
        } catch (Exception ignore) {
        }

        if (path != null && Files.exists(path)) {
            objectStorage.copyFromPath(path, targetObjectKey);
            return;
        }
        if (objectStorage.exists(objectKeyOrPath)) {
            objectStorage.putBytes(targetObjectKey, objectStorage.getBytes(objectKeyOrPath));
            return;
        }
        throw new ResponseException(ErrorCode.FAIL, "覆盖产物不存在: " + objectKeyOrPath);
    }

    private CoverageSnapshotEntity getSnapshotCached(String snapshotKey) {
        CoverageSnapshotEntity cached = snapshotCache.getIfPresent(snapshotKey);
        if (cached != null) {
            return cached;
        }
        CoverageSnapshotEntity parsed = CoverageSnapshotCodec.fromGzipBytes(objectStorage.getBytes(snapshotKey));
        snapshotCache.put(snapshotKey, parsed);
        return parsed;
    }

    private String buildScopeKey(String gitUrl, String branch, String type, String fromType) {
        return gitUrl + "|" + branch + "|" + type + "|" + fromType;
    }

    private void sortRows(List<CoverageNodeStatsEntity> rows, String sortBy, String order) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Comparator<CoverageNodeStatsEntity> comparator;
        if ("name".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(CoverageNodeStatsEntity::getDisplayName, Comparator.nullsLast(String::compareTo));
        } else {
            comparator = Comparator.comparingDouble(v -> coverageRate(v.getLineCovered(), v.getLineMissed()));
        }
        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator);
    }

    private String normalizeClassKey(String filePath) {
        String normalized = filePath.replace('\\', '/');
        if (normalized.endsWith(".java")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        return normalized.replace('/', '.');
    }

    private double coverageRate(Integer covered, Integer missed) {
        int c = covered == null ? 0 : covered;
        int m = missed == null ? 0 : missed;
        int t = c + m;
        if (t <= 0) {
            return 0D;
        }
        return ((double) c) / t;
    }

    private int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }

    private Map<String, Map<Integer, String>> loadSourceLines(String repoLocalPath) {
        if (!StringUtils.hasText(repoLocalPath)) {
            return Collections.emptyMap();
        }
        Path repoRoot = Path.of(repoLocalPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(repoRoot)) {
            return Collections.emptyMap();
        }
        Map<String, Map<Integer, String>> out = new HashMap<>();
        try {
            Files.walk(repoRoot)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        String normalized = p.toString().replace('\\', '/');
                        int idx = normalized.indexOf("/src/main/java/");
                        if (idx < 0) {
                            return;
                        }
                        String key = normalized.substring(idx + "/src/main/java/".length());
                        Map<Integer, String> lines = new HashMap<>();
                        try (BufferedReader br = Files.newBufferedReader(p)) {
                            String line;
                            int lineNo = 1;
                            while ((line = br.readLine()) != null) {
                                lines.put(lineNo++, line);
                            }
                        } catch (Exception ignore) {
                        }
                        out.put(key, lines);
                    });
        } catch (Exception e) {
            log.warn("load source lines failed, repo={}", repoRoot, e);
        }
        return out;
    }

    private SummaryRate computeRate(CoverageSnapshotEntity snapshot) {
        int covered = 0;
        int missed = 0;
        int branchCovered = 0;
        int branchMissed = 0;
        for (CoverageSnapshotEntity.FileCoverage fileCoverage : snapshot.getFiles().values()) {
            for (CoverageSnapshotEntity.LineCoverage lineCoverage : fileCoverage.getLines().values()) {
                if ("COVERED".equals(lineCoverage.getStatus())) {
                    covered++;
                } else {
                    missed++;
                }
                branchCovered += nullSafe(lineCoverage.getBranchCovered());
                branchMissed += nullSafe(lineCoverage.getBranchMissed());
            }
        }
        double lineRate = covered + missed == 0 ? 0D : ((double) covered) / (covered + missed);
        double branchRate = branchCovered + branchMissed == 0 ? 0D : ((double) branchCovered) / (branchCovered + branchMissed);
        SummaryRate rate = new SummaryRate();
        rate.lineCoverageRate = lineRate;
        rate.branchCoverageRate = branchRate;
        return rate;
    }

    private static class SummaryRate {
        private Double lineCoverageRate;
        private Double branchCoverageRate;
    }
}
