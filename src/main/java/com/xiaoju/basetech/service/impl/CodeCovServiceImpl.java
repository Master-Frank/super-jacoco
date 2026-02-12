package com.xiaoju.basetech.service.impl;


import com.google.common.base.Preconditions;
import com.xiaoju.basetech.dao.CoverageReportDao;
import com.xiaoju.basetech.dao.DeployInfoDao;
import com.xiaoju.basetech.config.CovPathProperties;
import com.xiaoju.basetech.entity.*;
import com.xiaoju.basetech.service.CodeCovService;
import com.xiaoju.basetech.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.jacoco.core.tools.ExecFileLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static com.xiaoju.basetech.util.Constants.*;


/**
 * @description:
 * @author: gaoweiwei_v
 * @time: 2019/7/9 3:26 PM
 */
@Slf4j
@Service
public class CodeCovServiceImpl implements CodeCovService {
    //普通命令超时时间是10分钟,600000L 143
    private static final Long CMD_TIMEOUT = 600000L;

    @Autowired
    private CoverageReportDao coverageReportDao;
    @Autowired
    private DeployInfoDao deployInfoDao;

    @Autowired
    private CovPathProperties covPathProperties;

    @Autowired
    @Qualifier("covJobExecutor")
    private Executor covJobExecutor;

    @Autowired
    private DiffMethodsCalculator diffMethodsCalculator;
    @Autowired
    private CodeCloneExecutor codeCloneExecutor;

    @Autowired
    private CodeCompilerExecutor codeCompilerExecutor;

    @Autowired
    private ReportCopyExecutor reportCopyExecutor;

    @Autowired
    private MavenModuleUtil mavenModuleUtil;

    @Autowired
    private UnitTester unitTester;

    @Autowired
    private ReportParser reportParser;

    private static class GeneratedReportSummary {
        private final Path htmlIndex;
        private final double lineCoveragePercent;
        private final double branchCoveragePercent;
        private final long lineMissed;
        private final long lineCovered;
        private final long branchMissed;
        private final long branchCovered;

        private GeneratedReportSummary(Path htmlIndex,
                                      double lineCoveragePercent,
                                      double branchCoveragePercent,
                                      long lineMissed,
                                      long lineCovered,
                                      long branchMissed,
                                      long branchCovered) {
            this.htmlIndex = htmlIndex;
            this.lineCoveragePercent = lineCoveragePercent;
            this.branchCoveragePercent = branchCoveragePercent;
            this.lineMissed = lineMissed;
            this.lineCovered = lineCovered;
            this.branchMissed = branchMissed;
            this.branchCovered = branchCovered;
        }

        public Path getHtmlIndex() {
            return htmlIndex;
        }

        public double getLineCoveragePercent() {
            return lineCoveragePercent;
        }

        public double getBranchCoveragePercent() {
            return branchCoveragePercent;
        }

        public long getLineMissed() {
            return lineMissed;
        }

        public long getLineCovered() {
            return lineCovered;
        }

        public long getBranchMissed() {
            return branchMissed;
        }

        public long getBranchCovered() {
            return branchCovered;
        }
    }

    private static double percent(long missed, long covered) {
        long total = missed + covered;
        if (total <= 0) {
            return 100.0;
        }
        return ((double) covered) * 100.0 / ((double) total);
    }

    private GeneratedReportSummary generateReport(Path workDir,
                                                  List<Path> execFiles,
                                                  List<String> modules,
                                                  String moduleNameForSingle,
                                                  String reportName,
                                                  String diffFiles,
                                                  Path outputDir) throws Exception {
        Path outputRoot = resolveReportOutputRoot(workDir, outputDir);
        if (modules == null || modules.isEmpty()) {
            JacocoInProcessReportGenerator.ReportResult reportResult = JacocoInProcessReportGenerator.generateHtmlAndXml(
                    outputRoot,
                    execFiles,
                    buildClassDirs(workDir, modules),
                    buildSourceDirs(workDir, modules),
                    moduleNameForSingle,
                    reportName,
                    diffFiles,
                    outputDir
            );
            return new GeneratedReportSummary(
                    reportResult.getHtmlIndex(),
                    reportResult.getLineCoveragePercent(),
                    reportResult.getBranchCoveragePercent(),
                    reportResult.getLineCounter().getMissedCount(),
                    reportResult.getLineCounter().getCoveredCount(),
                    reportResult.getBranchCounter().getMissedCount(),
                    reportResult.getBranchCounter().getCoveredCount()
            );
        }

        if (modules.size() == 1) {
            String moduleName = !StringUtils.isEmpty(moduleNameForSingle) ? moduleNameForSingle : modules.get(0);
            JacocoInProcessReportGenerator.ReportResult reportResult = JacocoInProcessReportGenerator.generateHtmlAndXml(
                    outputRoot,
                    execFiles,
                    buildClassDirs(workDir, modules),
                    buildSourceDirs(workDir, modules),
                    moduleName,
                    reportName,
                    diffFiles,
                    outputDir
            );
            return new GeneratedReportSummary(
                    reportResult.getHtmlIndex(),
                    reportResult.getLineCoveragePercent(),
                    reportResult.getBranchCoveragePercent(),
                    reportResult.getLineCounter().getMissedCount(),
                    reportResult.getLineCounter().getCoveredCount(),
                    reportResult.getBranchCounter().getMissedCount(),
                    reportResult.getBranchCounter().getCoveredCount()
            );
        }

        SafeFileOps.deleteRecursively(outputRoot, outputDir);
        Files.createDirectories(outputDir);

        ArrayList<String> indexFiles = new ArrayList<>();
        long totalLineMissed = 0;
        long totalLineCovered = 0;
        long totalBranchMissed = 0;
        long totalBranchCovered = 0;
        for (String module : modules) {
            Path moduleOutputDir = outputDir.resolve(module);
            JacocoInProcessReportGenerator.ReportResult reportResult = JacocoInProcessReportGenerator.generateHtmlAndXml(
                    outputRoot,
                    execFiles,
                    buildClassDirs(workDir, Collections.singletonList(module)),
                    buildSourceDirs(workDir, Collections.singletonList(module)),
                    module,
                    reportName,
                    diffFiles,
                    moduleOutputDir
            );
            if (reportResult != null && reportResult.getHtmlIndex() != null) {
                indexFiles.add(reportResult.getHtmlIndex().toString());
                totalLineMissed += reportResult.getLineCounter().getMissedCount();
                totalLineCovered += reportResult.getLineCounter().getCoveredCount();
                totalBranchMissed += reportResult.getBranchCounter().getMissedCount();
                totalBranchCovered += reportResult.getBranchCounter().getCoveredCount();
            }
        }

        if (indexFiles.isEmpty()) {
            throw new IllegalStateException("未生成任何模块报告");
        }

        Path firstIndex = Paths.get(indexFiles.get(0)).toAbsolutePath().normalize();
        Path firstDir = firstIndex.getParent();
        if (firstDir != null) {
            Path resourcesDir = firstDir.resolve("jacoco-resources");
            if (Files.exists(resourcesDir) && Files.isDirectory(resourcesDir)) {
                Path targetResourcesDir = outputDir.resolve("jacoco-resources");
                SafeFileOps.deleteRecursively(outputRoot, targetResourcesDir);
                SafeFileOps.copyDirectory(outputRoot, resourcesDir, outputRoot, targetResourcesDir);
            }
            Path sessions = firstDir.resolve("jacoco-sessions.html");
            if (Files.exists(sessions) && Files.isRegularFile(sessions)) {
                Files.copy(sessions, outputDir.resolve("jacoco-sessions.html"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }

        Path mergedIndex = outputDir.resolve("index.html");
        MergeReportHtml.mergeHtml(indexFiles, mergedIndex.toString());
        double mergedLine = percent(totalLineMissed, totalLineCovered);
        double mergedBranch = percent(totalBranchMissed, totalBranchCovered);
        return new GeneratedReportSummary(
                mergedIndex,
                mergedLine,
                mergedBranch,
                totalLineMissed,
                totalLineCovered,
                totalBranchMissed,
                totalBranchCovered
        );
    }

    private Path resolveReportOutputRoot(Path codeDir, Path outputDir) {
        Path normalizedCodeDir = codeDir.toAbsolutePath().normalize();
        Path normalizedOutputDir = outputDir.toAbsolutePath().normalize();
        if (normalizedOutputDir.startsWith(normalizedCodeDir)) {
            return normalizedCodeDir;
        }

        Path reportRoot = covPathProperties.reportRootPath();
        if (normalizedOutputDir.startsWith(reportRoot)) {
            return reportRoot;
        }

        throw new IllegalArgumentException("outputDir must be under codeDir or reportRoot");
    }

    /**
     * 新增单元覆盖率增量覆盖率任务
     *
     * @param unitCoverRequest
     */
    @Override
    public void triggerUnitCov(UnitCoverRequest unitCoverRequest) {
        CoverageReportEntity history = coverageReportDao.queryCoverageReportByUuid(unitCoverRequest.getUuid());
        if (history != null) {
            throw new ResponseException(ErrorCode.FAIL, String.format("uuid:%s已经调用过，请勿重复触发！",
                    unitCoverRequest.getUuid()));
        }

        CoverageReportEntity coverageReport = new CoverageReportEntity();
        try {
            BeanUtils.copyProperties(coverageReport, unitCoverRequest);
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.error("copy properties failed, uuid={}", unitCoverRequest.getUuid(), e);
            throw new ResponseException(ErrorCode.FAIL, "参数处理失败");
        }
        coverageReport.setFrom(Constants.CoverageFrom.UNIT.val());
        coverageReport.setRequestStatus(Constants.JobStatus.INITIAL.val());
        if (StringUtils.isEmpty(coverageReport.getSubModule())) {
            coverageReport.setSubModule("");
        }
        coverageReportDao.insertCoverageReportById(coverageReport);
    }

    /**
     * 获取覆盖率结果
     *
     * @param uuid
     * @return
     */
    @Override
    public CoverResult getCoverResult(String uuid) {
        Preconditions.checkArgument(!StringUtils.isEmpty(uuid), "uuid不能为空");

        CoverageReportEntity coverageReport = coverageReportDao.queryCoverageReportByUuid(uuid);
        CoverResult result = new CoverResult();
        if (coverageReport == null) {
            result.setCoverStatus(-1);
            result.setLineCoverage(-1);
            result.setBranchCoverage(-1);
            result.setErrMsg("uuid对应的报告不存在");
            return result;
        }

        try {
            BeanUtils.copyProperties(result, coverageReport);
            result.setLogFile(toPublicUrl(coverageReport.getLogFile()));
            int status = coverageReport.getRequestStatus() == null ? -1 : coverageReport.getRequestStatus();
            if (status == Constants.JobStatus.NODIFF.val()) {
                result.setCoverStatus(1);
                if (StringUtils.isEmpty(coverageReport.getErrMsg())) {
                    result.setErrMsg("没有增量代码");
                }
            } else if (status < Constants.JobStatus.SUCCESS.val()) {
                result.setCoverStatus(0);
                result.setErrMsg("正在统计增量覆盖率..." + Constants.JobStatus.desc(status));
            } else if (status > Constants.JobStatus.SUCCESS.val()) {
                result.setCoverStatus(-1);
                result.setErrMsg("统计失败:" + coverageReport.getErrMsg());
                result.setBranchCoverage(-1);
                result.setLineCoverage(-1);
            } else {
                result.setCoverStatus(1);
            }
            return result;

        } catch (Exception e) {
            log.error("getCoverResult failed, uuid={}", uuid, e);
            throw new ResponseException(ErrorCode.FAIL, "查询失败");
        }
    }

    private String toPublicUrl(String pathOrUrl) {
        if (StringUtils.isEmpty(pathOrUrl)) {
            return "";
        }
        String trimmed = pathOrUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        try {
            Path raw = Paths.get(trimmed).toAbsolutePath().normalize();
            Path reportRoot = covPathProperties.reportRootPath();
            if (raw.startsWith(reportRoot)) {
                String rel = reportRoot.relativize(raw).toString().replace('\\', '/');
                return LocalIpUtils.getTomcatBaseUrl() + rel;
            }
        } catch (Exception ignore) {
        }
        return trimmed;
    }


    /**
     * 计算覆盖率具体步骤
     *
     * @param coverageReport
     */
    @Override
    public void calculateUnitCover(CoverageReportEntity coverageReport) {
        long s = System.currentTimeMillis();
        log.info("{}计算覆盖率具体步骤...开始执行uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());

        try {
            // 下载代码
            coverageReport.setRequestStatus(Constants.JobStatus.CLONING.val());
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            codeCloneExecutor.cloneCode(coverageReport);
            // 更新状态
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            if (coverageReport.getRequestStatus() != Constants.JobStatus.CLONE_DONE.val()) {
                log.info("{}计算覆盖率具体步骤...克隆失败uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
                return;
            }

            // 计算增量方法
            coverageReport.setRequestStatus(Constants.JobStatus.DIFF_METHODS_EXECUTING.val());
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            diffMethodsCalculator.executeDiffMethods(coverageReport);
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            if (coverageReport.getRequestStatus() != Constants.JobStatus.DIFF_METHOD_DONE.val()) {
                log.info("{}计算覆盖率具体步骤...计算增量方法uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
                return;
            }
            // 添加集成模块
            coverageReport.setRequestStatus(Constants.JobStatus.ADDMODULING.val());
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            mavenModuleUtil.addMavenModule(coverageReport);
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            if (coverageReport.getRequestStatus() != Constants.JobStatus.ADDMODULE_DONE.val()) {
                log.info("{}计算覆盖率具体步骤...添加集成模块失败uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
                return;
            }

            // 执行单元测试
            coverageReport.setRequestStatus(Constants.JobStatus.UNITTESTEXECUTING.val());
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            unitTester.executeUnitTest(coverageReport);
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            if (coverageReport.getRequestStatus() != Constants.JobStatus.UNITTEST_DONE.val()) {
                log.info("{}计算覆盖率具体步骤...单元测试失败uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
                return;
            }

            Path workDir = resolveMavenWorkDir(coverageReport);
            List<Path> execFiles = JacocoInProcessReportGenerator.findJacocoExecFiles(workDir);
            if (execFiles == null || execFiles.isEmpty()) {
                coverageReport.setRequestStatus(Constants.JobStatus.GENERATEREPORT_FAIL.val());
                coverageReport.setErrMsg("项目没有单元测试case");
                coverageReportDao.updateCoverageReportByReport(coverageReport);
                return;
            }

            List<String> modules = MavenModuleUtil.getValidModules(workDir.resolve("pom.xml").toString());
            modules = normalizeModules(modules);

            String moduleNameForSingle = StringUtils.isEmpty(coverageReport.getSubModule()) ? null : coverageReport.getSubModule();

            String reportName = coverageReport.getType() != null && coverageReport.getType() == Constants.ReportType.DIFF.val() ? "UnitDiffCoverage" : "UnitCoverage";
            Path outputDir = covPathProperties.reportRootPath().resolve(coverageReport.getUuid());
            GeneratedReportSummary reportSummary = generateReport(
                    workDir,
                    execFiles,
                    modules,
                    moduleNameForSingle,
                    reportName,
                    coverageReport.getDiffMethod(),
                    outputDir
            );
            coverageReport.setReportFile(reportSummary.getHtmlIndex().toString());
            coverageReport.setLineCoverage(reportSummary.getLineCoveragePercent());
            coverageReport.setBranchCoverage(reportSummary.getBranchCoveragePercent());

            //分析覆盖率报告
            coverageReport.setRequestStatus(Constants.JobStatus.REPORTPARSING.val());
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            reportParser.parseReport(coverageReport);
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            if (coverageReport.getRequestStatus() != Constants.JobStatus.PARSEREPORT_DONE.val()) {
                log.info("{}计算覆盖率具体步骤...分析报告失败uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
                return;
            }

            //复制报告到指定目录
            coverageReport.setRequestStatus(Constants.JobStatus.REPORTCOPYING.val());
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            reportCopyExecutor.copyReport(coverageReport);
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            if (coverageReport.getRequestStatus() != Constants.JobStatus.COPYREPORT_DONE.val()) {
                log.info("{}计算覆盖率具体步骤...复制报告失败uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
                return;
            }

            coverageReport.setRequestStatus(Constants.JobStatus.SUCCESS.val());
        } catch (Exception e) {
            log.error("uuid={}任务执行异常", coverageReport == null ? null : coverageReport.getUuid(), e);
            if (coverageReport != null) {
                Integer status = coverageReport.getRequestStatus();
                if (status == null) {
                    coverageReport.setRequestStatus(Constants.JobStatus.GENERATEREPORT_FAIL.val());
                } else if (status > Constants.JobStatus.SUCCESS.val()) {
                    // keep
                } else if (status == Constants.JobStatus.CLONING.val()) {
                    coverageReport.setRequestStatus(Constants.JobStatus.CLONE_FAIL.val());
                } else if (status == Constants.JobStatus.DIFF_METHODS_EXECUTING.val()) {
                    coverageReport.setRequestStatus(Constants.JobStatus.DIFF_METHOD_FAIL.val());
                } else if (status == Constants.JobStatus.ADDMODULING.val()) {
                    coverageReport.setRequestStatus(Constants.JobStatus.FAILADDMODULE.val());
                } else if (status == Constants.JobStatus.UNITTESTEXECUTING.val()) {
                    coverageReport.setRequestStatus(Constants.JobStatus.UNITTEST_FAIL.val());
                } else if (status == Constants.JobStatus.REPORTGENERATING.val()) {
                    coverageReport.setRequestStatus(Constants.JobStatus.GENERATEREPORT_FAIL.val());
                } else if (status == Constants.JobStatus.REPORTPARSING.val()) {
                    coverageReport.setRequestStatus(Constants.JobStatus.FAILPARSEREPOAT.val());
                } else if (status == Constants.JobStatus.REPORTCOPYING.val()) {
                    coverageReport.setRequestStatus(Constants.JobStatus.COPYREPORT_FAIL.val());
                } else if (status != Constants.JobStatus.SUCCESS.val()) {
                    coverageReport.setRequestStatus(Constants.JobStatus.GENERATEREPORT_FAIL.val());
                }
                coverageReport.setErrMsg("任务执行异常:" + e.getMessage());
            }
        } finally {
            if (coverageReport != null) {
                try {
                    safeDeleteCodeDir(coverageReport);
                } catch (IOException e) {
                    log.error("uuid={}删除代码失败..", coverageReport.getUuid(), e);
                    String existing = coverageReport.getErrMsg();
                    String msg = "删除代码失败:" + e.getMessage();
                    if (StringUtils.isEmpty(existing)) {
                        coverageReport.setErrMsg(msg);
                    } else {
                        coverageReport.setErrMsg(existing + "; " + msg);
                    }
                }
                coverageReportDao.updateCoverageReportByReport(coverageReport);
            }
            log.info("{}计算覆盖率具体步骤...执行结束，耗时{}ms", Thread.currentThread().getName(),
                    System.currentTimeMillis() - s);
        }
    }

    @Override
    public void calculateDeployDiffMethods(CoverageReportEntity coverageReport) {
        // 计算增量方法
        coverageReport.setRequestStatus(Constants.JobStatus.DIFF_METHODS_EXECUTING.val());
        coverageReportDao.updateCoverageReportByReport(coverageReport);
        diffMethodsCalculator.executeDiffMethods(coverageReport);
        coverageReportDao.updateCoverageReportByReport(coverageReport);
    }

    @Override
    public void cloneAndCompileCode(CoverageReportEntity coverageReport) {
        // 下载代码
        coverageReport.setRequestStatus(Constants.JobStatus.CLONING.val());
        coverageReportDao.updateCoverageReportByReport(coverageReport);
        codeCloneExecutor.cloneCode(coverageReport);
        // 更新状态
        coverageReportDao.updateCoverageReportByReport(coverageReport);
        if (coverageReport.getRequestStatus() != Constants.JobStatus.CLONE_DONE.val()) {
            log.info("{}计算覆盖率具体步骤...克隆失败uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
            return;
        }
        //编译代码
        coverageReport.setRequestStatus(Constants.JobStatus.COMPILING.val());
        coverageReportDao.updateCoverageReportByReport(coverageReport);
        codeCompilerExecutor.compileCode(coverageReport);
        coverageReportDao.updateCoverageReportByReport(coverageReport);
        if (coverageReport.getRequestStatus() != JobStatus.COMPILE_DONE.val()) {
            log.info("{}计算覆盖率具体步骤...编译失败uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
            return;
        }
        DeployInfoEntity deployInfo = new DeployInfoEntity();
        deployInfo.setUuid(coverageReport.getUuid());
        deployInfo.setCodePath(Paths.get(coverageReport.getNowLocalPath()).toAbsolutePath().normalize().toString());
        String pomPath = deployInfo.getCodePath() + "/pom.xml";
        ArrayList<String> moduleList = MavenModuleUtil.getValidModules(pomPath);
        StringBuilder moduleNames = new StringBuilder("");
        for (String module : moduleList) {
            moduleNames.append(module + ",");
        }
        deployInfo.setChildModules(moduleNames.toString());
        int i = deployInfoDao.updateDeployInfo(deployInfo);
        if (i < 1) {
            log.info("{}计算覆盖率具体步骤...获取ChildModules失败uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
            return;
        }
    }

    /**
     * @param envCoverRequest
     */

    @Override
    public void triggerEnvCov(EnvCoverRequest envCoverRequest) {
        try {
            CoverageReportEntity coverageReport = new CoverageReportEntity();
            coverageReport.setFrom(Constants.CoverageFrom.ENV.val());
            coverageReport.setEnvType("");
            coverageReport.setUuid(envCoverRequest.getUuid());
            coverageReport.setGitUrl(envCoverRequest.getGitUrl());
            coverageReport.setNowVersion(envCoverRequest.getNowVersion());
            coverageReport.setType(envCoverRequest.getType());

            if (!StringUtils.isEmpty(envCoverRequest.getBaseVersion())) {
                coverageReport.setBaseVersion(envCoverRequest.getBaseVersion());
            }
            if (!StringUtils.isEmpty(envCoverRequest.getSubModule())) {
                coverageReport.setSubModule(envCoverRequest.getSubModule());
            }

            if (envCoverRequest.getBaseVersion().equals(envCoverRequest.getNowVersion()) && envCoverRequest.getType() == Constants.ReportType.DIFF.val()) {
                coverageReport.setBranchCoverage((double) 100);
                coverageReport.setLineCoverage((double) 100);
                coverageReport.setRequestStatus(Constants.JobStatus.NODIFF.val());
                coverageReport.setErrMsg("没有增量方法");
                coverageReportDao.insertCoverageReportById(coverageReport);
                return;
            }

            coverageReport.setRequestStatus(Constants.JobStatus.WAITING.val());
            coverageReportDao.insertCoverageReportById(coverageReport);
            deployInfoDao.insertDeployId(envCoverRequest.getUuid(), envCoverRequest.getAddress(), envCoverRequest.getPort());
            covJobExecutor.execute(() -> {
                boolean handedOff = false;
                try {
                    cloneAndCompileCode(coverageReport);
                    if (coverageReport.getRequestStatus() != Constants.JobStatus.COMPILE_DONE.val()) {
                        log.info("{}计算覆盖率具体步骤...编译失败uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
                        return;
                    }
                    if (coverageReport.getType() == Constants.ReportType.DIFF.val()) {
                        calculateDeployDiffMethods(coverageReport);
                        if (coverageReport.getRequestStatus() != Constants.JobStatus.DIFF_METHOD_DONE.val()) {
                            log.info("{}计算覆盖率具体步骤...计算增量代码失败，uuid={}", Thread.currentThread().getName(), coverageReport.getUuid());
                            return;
                        }
                    }
                    coverageReport.setRequestStatus(Constants.JobStatus.WAITING_PULL_EXEC.val());
                    coverageReportDao.updateCoverageReportByReport(coverageReport);
                    handedOff = true;
                } catch (Exception e) {
                    log.error("triggerEnvCov async failed, uuid={}", coverageReport.getUuid(), e);
                    coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
                    coverageReport.setErrMsg("任务初始化失败");
                    coverageReportDao.updateCoverageReportByReport(coverageReport);
                } finally {
                    if (!handedOff) {
                        try {
                            safeDeleteCodeDir(coverageReport);
                        } catch (IOException e) {
                            log.error("uuid={}删除代码失败..", coverageReport.getUuid(), e);
                            String existing = coverageReport.getErrMsg();
                            String msg = "删除代码失败:" + e.getMessage();
                            if (StringUtils.isEmpty(existing)) {
                                coverageReport.setErrMsg(msg);
                            } else {
                                coverageReport.setErrMsg(existing + "; " + msg);
                            }
                        }
                        coverageReportDao.updateCoverageReportByReport(coverageReport);
                    }
                }
            });
        } catch (Exception e) {
            log.error("triggerEnvCov failed, uuid={}", envCoverRequest == null ? null : envCoverRequest.getUuid(), e);
            throw new ResponseException(ErrorCode.FAIL, "触发失败");
        }

    }

    /**
     * 从项目机器上拉取功能测试的执行轨迹.exec文件，计算增量方法覆盖率
     *
     * @param coverageReport
     * @return
     */
    @Override
    public void calculateEnvCov(CoverageReportEntity coverageReport) {
        String uuid = coverageReport.getUuid();
        if (StringUtils.isEmpty(coverageReport.getLogFile())) {
            coverageReport.setLogFile(LocalIpUtils.getTomcatBaseUrl() + "logs/" + uuid + ".log");
        }
        Path logFilePath = null;
        try {
            String logFile = coverageReport.getLogFile().replace(LocalIpUtils.getTomcatBaseUrl() + "logs/", covPathProperties.getLogRoot());
            logFilePath = StringUtils.isEmpty(logFile) ? null : Paths.get(logFile);
            if (logFilePath != null) {
                Path parent = logFilePath.getParent();
                if (parent != null) {
                    java.nio.file.Files.createDirectories(parent);
                }
            }
        } catch (Exception ignored) {
            logFilePath = null;
        }

        coverageReport.setRequestStatus(Constants.JobStatus.REPORTGENERATING.val());
        coverageReportDao.updateCoverageReportByReport(coverageReport);

        DeployInfoEntity deployInfoEntity = deployInfoDao.queryDeployId(uuid);
        if (deployInfoEntity == null) {
            coverageReport.setErrMsg("未找到部署信息");
            coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
            coverageReportDao.updateCoverageReportByReport(coverageReport);
            return;
        }
        String reportName = "ManualDiffCoverage";
        if (coverageReport.getType() == 1) {
            reportName = "ManualCoverage";
        }

        try {
            Path codeDir = null;
            try {
                if (deployInfoEntity.getCodePath() != null) {
                    codeDir = Paths.get(deployInfoEntity.getCodePath()).toAbsolutePath().normalize();
                }
            } catch (Exception ignore) {
                codeDir = null;
            }
            if (codeDir == null) {
                codeDir = Paths.get(coverageReport.getNowLocalPath()).toAbsolutePath().normalize();
            }

            List<String> dumpCmd = new ArrayList<>();
            dumpCmd.add("java");
            dumpCmd.add("-jar");
            dumpCmd.add(covPathProperties.getJacocoCliJar());
            dumpCmd.add("dump");
            dumpCmd.add("--address");
            dumpCmd.add(deployInfoEntity.getAddress());
            dumpCmd.add("--port");
            dumpCmd.add(String.valueOf(deployInfoEntity.getPort()));
            dumpCmd.add("--destfile");
            dumpCmd.add("./jacoco.exec");
            int exitCode = CmdExecutor.executeCmd(dumpCmd, codeDir, CMD_TIMEOUT, logFilePath);

            if (exitCode == 0) {
                Path reportTargetDir = covPathProperties.reportRootPath().resolve(coverageReport.getUuid());
                SafeFileOps.deleteRecursively(covPathProperties.reportRootPath(), reportTargetDir);

                List<String> modules = new ArrayList<>();
                if (!StringUtils.isEmpty(coverageReport.getSubModule())) {
                    modules.add(coverageReport.getSubModule());
                } else {
                    modules = MavenModuleUtil.getValidModules(codeDir.resolve("pom.xml").toString());
                    if (modules == null || modules.isEmpty()) {
                        modules = splitModules(deployInfoEntity.getChildModules());
                    }
                }
                modules = normalizeModules(modules);

                Path htmlDir = covPathProperties.reportRootPath().resolve(coverageReport.getUuid());
                GeneratedReportSummary reportSummary = generateReport(
                        codeDir,
                        Collections.singletonList(codeDir.resolve("jacoco.exec")),
                        modules,
                        null,
                        reportName,
                        coverageReport.getDiffMethod(),
                        htmlDir
                );
                coverageReport.setReportUrl(LocalIpUtils.getTomcatBaseUrl() + coverageReport.getUuid() + "/index.html");
                coverageReport.setRequestStatus(Constants.JobStatus.SUCCESS.val());
                coverageReport.setLineCoverage(reportSummary.getLineCoveragePercent());
                coverageReport.setBranchCoverage(reportSummary.getBranchCoveragePercent());
                return;
            } else {
                coverageReport.setErrMsg("获取jacoco.exec 文件失败");
                coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
                log.error("uuid={}{}", coverageReport.getUuid(), coverageReport.getErrMsg());
            }
        } catch (java.util.concurrent.TimeoutException e) {
            coverageReport.setRequestStatus(Constants.JobStatus.TIMEOUT.val());
            coverageReport.setErrMsg("获取jacoco.exec 文件超时");
            log.error("uuid={}获取超时", coverageReport.getUuid(), e);
        } catch (IllegalArgumentException e) {
            coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
            coverageReport.setErrMsg("生成jacoco报告失败:" + e.getMessage());
            log.error("uuid={}生成jacoco报告失败", coverageReport.getUuid(), e);
        } catch (Exception e) {
            coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
            coverageReport.setErrMsg("获取jacoco.exec 文件发生未知错误");
            log.error("uuid={}获取jacoco.exec 文件发生未知错误", coverageReport.getUuid(), e);
        } finally {
            try {
                safeDeleteCodeDir(coverageReport);
            } catch (IOException e) {
                log.error("uuid={}删除代码失败..", coverageReport.getUuid(), e);
                String existing = coverageReport.getErrMsg();
                String msg = "删除代码失败:" + e.getMessage();
                if (StringUtils.isEmpty(existing)) {
                    coverageReport.setErrMsg(msg);
                } else {
                    coverageReport.setErrMsg(existing + "; " + msg);
                }
            }
            coverageReportDao.updateCoverageReportByReport(coverageReport);
        }
    }

    private void safeDeleteCodeDir(CoverageReportEntity coverageReport) throws IOException {
        if (coverageReport == null || StringUtils.isEmpty(coverageReport.getNowLocalPath())) {
            return;
        }
        Path codeRoot = covPathProperties.codeRootPath();
        Path now;
        try {
            now = Paths.get(coverageReport.getNowLocalPath()).toAbsolutePath().normalize();
        } catch (Exception e) {
            return;
        }
        Path target = now.getParent();
        if (target == null || !target.startsWith(codeRoot)) {
            return;
        }
        SafeFileOps.deleteRecursively(codeRoot, target);
    }

    private List<String> splitModules(String raw) {
        List<String> out = new ArrayList<>();
        if (StringUtils.isEmpty(raw)) {
            return out;
        }
        for (String v : raw.split(",")) {
            if (!StringUtils.isEmpty(v)) {
                String trimmed = v.trim();
                if (!StringUtils.isEmpty(trimmed)) {
                    out.add(trimmed);
                }
            }
        }
        return out;
    }

    private List<String> normalizeModules(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String v : raw) {
            if (!StringUtils.isEmpty(v)) {
                String trimmed = v.trim();
                if (!StringUtils.isEmpty(trimmed) && !"jacocomodule".equals(trimmed)) {
                    out.add(trimmed);
                }
            }
        }
        return out;
    }

    private Path resolveMavenWorkDir(CoverageReportEntity coverageReport) {
        Path root = Paths.get(coverageReport.getNowLocalPath()).toAbsolutePath().normalize();
        String subModule = coverageReport.getSubModule();
        if (StringUtils.isEmpty(subModule)) {
            return root;
        }
        Path candidate = root.resolve(subModule).toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            return root;
        }
        if (Files.exists(candidate.resolve("pom.xml"))) {
            return candidate;
        }
        return root;
    }

    private List<Path> buildClassDirs(Path codeDir, List<String> modules) {
        List<Path> out = new ArrayList<>();
        if (modules == null || modules.isEmpty()) {
            out.add(codeDir.resolve("target").resolve("classes"));
            return out;
        }
        for (String module : modules) {
            out.add(codeDir.resolve(module).resolve("target").resolve("classes"));
        }
        return out;
    }

    private List<Path> buildSourceDirs(Path codeDir, List<String> modules) {
        List<Path> out = new ArrayList<>();
        if (modules == null || modules.isEmpty()) {
            out.add(codeDir.resolve("src").resolve("main").resolve("java"));
            return out;
        }
        for (String module : modules) {
            out.add(codeDir.resolve(module).resolve("src").resolve("main").resolve("java"));
        }
        return out;
    }

    @Override
    public CoverResult getLocalCoverResult(LocalHostRequestParam localHostRequestParam) {
        InputValidator.requireSafeUuid(localHostRequestParam.getUuid());
        InputValidator.requireSafeGitUrl(localHostRequestParam.getGitUrl());
        InputValidator.requireSafeVersion(localHostRequestParam.getBaseVersion(), "baseVersion");
        InputValidator.requireSafeVersion(localHostRequestParam.getNowVersion(), "nowVersion");
        InputValidator.requireSafeSubModule(localHostRequestParam.getSubModule());

        Path basePath = InputValidator.requirePathWithinRoot(localHostRequestParam.getBasePath(), covPathProperties.localCovRootPath(), "basePath");
        Path nowPath = InputValidator.requirePathWithinRoot(localHostRequestParam.getNowPath(), covPathProperties.localCovRootPath(), "nowPath");
        InputValidator.requirePathWithinRoot(localHostRequestParam.getClassFilePath(), covPathProperties.localCovRootPath(), "classFilePath");

        CoverResult result = new CoverResult();

        String diffFiles;
        if (localHostRequestParam.getType() != null && localHostRequestParam.getType() == Constants.ReportType.FULL.val()) {
            diffFiles = "";
        } else {
            diffFiles = diffMethodsCalculator.executeDiffMethodsForEnv(localHostRequestParam.getGitUrl(), basePath.toString(), nowPath.toString(), localHostRequestParam.getBaseVersion(), localHostRequestParam.getNowVersion());
            if (diffFiles == null) {
                result.setCoverStatus(-1);
                result.setLineCoverage(-1);
                result.setBranchCoverage(-1);
                result.setErrMsg("计算增量方法失败");
                return result;
            }
            if (StringUtils.isEmpty(diffFiles)) {
                result.setCoverStatus(-1);
                result.setLineCoverage(-1);
                result.setBranchCoverage(-1);
                result.setErrMsg("未检测到增量代码");
                return result;
            }
        }
        //2、拉取jacoco.exec文件并解析
        if (StringUtils.isEmpty(localHostRequestParam.getAddress())) {
            localHostRequestParam.setAddress("127.0.0.1");
        }
        localHostRequestParam.setBasePath(basePath.toString());
        localHostRequestParam.setNowPath(nowPath.toString());
        CoverResult coverResult = pullExecFile(localHostRequestParam, diffFiles, localHostRequestParam.getSubModule());
        if (coverResult != null && coverResult.getCoverStatus() == 200 && !StringUtils.isEmpty(coverResult.getReportUrl())) {
            try {
                String reportUrl = coverResult.getReportUrl().trim();
                if (reportUrl.startsWith("http://") || reportUrl.startsWith("https://")) {
                    return coverResult;
                }

                Path index = Paths.get(reportUrl).toAbsolutePath().normalize();
                Path reportRoot = covPathProperties.reportRootPath();
                Path expectedDir = reportRoot.resolve(localHostRequestParam.getUuid()).toAbsolutePath().normalize();
                if (index.startsWith(expectedDir)) {
                    coverResult.setReportUrl(LocalIpUtils.getTomcatBaseUrl() + localHostRequestParam.getUuid() + "/index.html");
                    return coverResult;
                }

                Path sourceDir = index.getParent();
                if (sourceDir != null) {
                    Path reportTargetDir = reportRoot.resolve(localHostRequestParam.getUuid());
                    SafeFileOps.deleteRecursively(reportRoot, reportTargetDir);
                    SafeFileOps.copyDirectory(covPathProperties.localCovRootPath(), sourceDir, reportRoot, reportTargetDir);
                    coverResult.setReportUrl(LocalIpUtils.getTomcatBaseUrl() + localHostRequestParam.getUuid() + "/index.html");
                }
            } catch (Exception e) {
                log.error("uuid={}复制报告异常", localHostRequestParam.getUuid(), e);
            }
        }
        return coverResult;
    }

    /**
     * 拉取jacoco文件并转换为报告
     */
    private CoverResult pullExecFile(LocalHostRequestParam localHostRequestParam, String diffFiles, String subModule) {
        String reportName = localHostRequestParam.getType() != null && localHostRequestParam.getType() == Constants.ReportType.FULL.val()
                ? "ManualCoverage"
                : "ManualDiffCoverage";
        CoverResult coverResult = new CoverResult();
        try {
            List<String> dumpCmd = new ArrayList<>();
            dumpCmd.add("java");
            dumpCmd.add("-jar");
            dumpCmd.add(covPathProperties.getJacocoCliJar());
            dumpCmd.add("dump");
            dumpCmd.add("--address");
            dumpCmd.add(localHostRequestParam.getAddress());
            dumpCmd.add("--port");
            dumpCmd.add(String.valueOf(localHostRequestParam.getPort()));
            dumpCmd.add("--destfile");
            dumpCmd.add("./jacoco.exec");
            int exitCode = CmdExecutor.executeCmd(dumpCmd, Paths.get(localHostRequestParam.getNowPath()), CMD_TIMEOUT, null);

            if (exitCode == 0) {
                Path workDir = Paths.get(localHostRequestParam.getNowPath()).toAbsolutePath().normalize();

                List<String> modules = new ArrayList<>();
                if (!StringUtils.isEmpty(subModule)) {
                    modules.add(subModule);
                } else {
                    modules = MavenModuleUtil.getValidModules(workDir.resolve("pom.xml").toString());
                }
                modules = normalizeModules(modules);
                Path outputDir = covPathProperties.reportRootPath().resolve(localHostRequestParam.getUuid());
                GeneratedReportSummary reportSummary = generateReport(
                        workDir,
                        Collections.singletonList(workDir.resolve("jacoco.exec")),
                        modules,
                        subModule,
                        reportName,
                        diffFiles,
                        outputDir
                );

                coverResult.setCoverStatus(200);
                coverResult.setLineCoverage(reportSummary.getLineCoveragePercent());
                coverResult.setBranchCoverage(reportSummary.getBranchCoveragePercent());
                coverResult.setReportUrl(outputDir.resolve("index.html").toString());
                return coverResult;
            } else {
                coverResult.setCoverStatus(-1);
                coverResult.setErrMsg("拉取执行文件失败");
                log.error("获取jacoco.exec 文件失败，uuid={}", localHostRequestParam.getUuid());
                return coverResult;
            }
        } catch (IllegalArgumentException e) {
            coverResult.setCoverStatus(-1);
            coverResult.setLineCoverage(-1);
            coverResult.setBranchCoverage(-1);
            coverResult.setErrMsg(e.getMessage());
            log.error("uuid={}生成jacoco报告失败", localHostRequestParam.getUuid(), e);
            return coverResult;
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("获取jacoco.exec 文件失败，uuid={}获取超时", localHostRequestParam.getUuid(), e);
            throw new ResponseException(ErrorCode.FAIL, "获取jacoco.exec 文件超时");
        } catch (Exception e) {
            log.error("uuid={}获取jacoco.exec 文件发生未知错误", localHostRequestParam.getUuid(), e);
            throw new ResponseException(ErrorCode.FAIL, "拉取执行文件失败");
        }
    }

    private void mergeExec(List<String> ExecFiles, String NewFileName) {
        ExecFileLoader execFileLoader = new ExecFileLoader();
        try {
            for (String ExecFile : ExecFiles) {
                execFileLoader.load(new File(ExecFile));
            }
        } catch (Exception e) {
            log.error("ExecFiles 合并失败 errorMessege is {}", e.fillInStackTrace());
        }
        try {
            execFileLoader.save(new File(NewFileName), false);
        } catch (Exception e) {
            log.error("ExecFiles 保存失败 errorMessege is {}", e.fillInStackTrace());
        }
    }
}
