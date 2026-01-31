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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
        try {
            coverageReport.setRequestStatus(Constants.JobStatus.SUCCESS.val());
            safeDeleteCodeDir(coverageReport);
        } catch (IOException e) {
            log.error("uuid={}删除代码失败..", coverageReport.getUuid(), e);
            coverageReport.setRequestStatus(Constants.JobStatus.REMOVE_FILE_FAIL.val());
        }
        coverageReportDao.updateCoverageReportByReport(coverageReport);
        log.info("{}计算覆盖率具体步骤...执行完成，耗时{}ms", Thread.currentThread().getName(),
                System.currentTimeMillis() - s);
        return;

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
        deployInfo.setCodePath(coverageReport.getNowLocalPath());
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
                } catch (Exception e) {
                    log.error("triggerEnvCov async failed, uuid={}", coverageReport.getUuid(), e);
                    coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
                    coverageReport.setErrMsg("任务初始化失败");
                    coverageReportDao.updateCoverageReportByReport(coverageReport);
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
        String logFile = coverageReport.getLogFile().replace(LocalIpUtils.getTomcatBaseUrl() + "logs/", covPathProperties.getLogRoot());
        Path logFilePath = StringUtils.isEmpty(logFile) ? null : Paths.get(logFile);

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
            int exitCode = CmdExecutor.executeCmd(dumpCmd, Paths.get(coverageReport.getNowLocalPath()), CMD_TIMEOUT, logFilePath);

            if (exitCode == 0) {
                Path reportTargetDir = covPathProperties.reportRootPath().resolve(coverageReport.getUuid());
                SafeFileOps.deleteRecursively(covPathProperties.reportRootPath(), reportTargetDir);

                List<String> modules = splitModules(deployInfoEntity.getChildModules());
                List<String> reportCmd = buildJacocoReportCmd("./jacoco.exec", modules, coverageReport.getDiffMethod(), reportName, "./jacocoreport/");
                int covExitCode = CmdExecutor.executeCmd(reportCmd, Paths.get(deployInfoEntity.getCodePath()), CMD_TIMEOUT, logFilePath);
                File reportFile = new File(deployInfoEntity.getCodePath() + "/jacocoreport/index.html");

                if (covExitCode == 0 && reportFile.exists()) {
                    try {
                        Document doc = Jsoup.parse(reportFile.getAbsoluteFile(), "UTF-8", "");
                        Elements bars = doc.getElementById("coveragetable").getElementsByTag("tfoot").first().getElementsByClass("bar");
                        Elements lineCtr1 = doc.getElementById("coveragetable").getElementsByTag("tfoot").first().getElementsByClass("ctr1");
                        Elements lineCtr2 = doc.getElementById("coveragetable").getElementsByTag("tfoot").first().getElementsByClass("ctr2");
                        double lineCoverage = 100;
                        double branchCoverage = 100;
                        if (doc != null && bars != null) {
                            float lineNumerator = Float.valueOf(lineCtr1.get(1).text().replace(",", ""));
                            float lineDenominator = Float.valueOf(lineCtr2.get(3).text().replace(",", ""));
                            lineCoverage = (lineDenominator - lineNumerator) / lineDenominator * 100;
                            String[] branch = bars.get(1).text().split(" of ");
                            float branchNumerator = Float.valueOf(branch[0].replace(",", ""));
                            float branchDenominator = Float.valueOf(branch[1].replace(",", ""));
                            if (branchDenominator > 0.0) {
                                branchCoverage = (branchDenominator - branchNumerator) / branchDenominator * 100;
                            }
                        }

                        SafeFileOps.copyDirectory(covPathProperties.codeRootPath(), reportFile.getParentFile().toPath(), covPathProperties.reportRootPath(), reportTargetDir);
                        coverageReport.setReportUrl(LocalIpUtils.getTomcatBaseUrl() + coverageReport.getUuid() + "/index.html");
                        coverageReport.setRequestStatus(Constants.JobStatus.SUCCESS.val());
                        coverageReport.setLineCoverage(lineCoverage);
                        coverageReport.setBranchCoverage(branchCoverage);
                        return;
                    } catch (Exception e) {
                        coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
                        coverageReport.setErrMsg("解析jacoco报告失败");
                        log.error("uuid={}解析jacoco报告失败", coverageReport.getUuid(), e);
                    }
                } else {
                    coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
                    if (modules.isEmpty()) {
                        coverageReport.setErrMsg("生成jacoco报告失败");
                        return;
                    }
                    boolean allOk = true;
                    ArrayList<String> childReportList = new ArrayList<>();

                    for (String module : modules) {
                        List<String> moduleReportCmd = buildJacocoReportCmd("./jacoco.exec", java.util.Collections.singletonList(module), coverageReport.getDiffMethod(), reportName, "jacocoreport/" + module);
                        int moduleExitCode = CmdExecutor.executeCmd(moduleReportCmd, Paths.get(deployInfoEntity.getCodePath()), CMD_TIMEOUT, logFilePath);
                        if (moduleExitCode == 0) {
                            childReportList.add(deployInfoEntity.getCodePath() + "/jacocoreport/" + module + "/index.html");
                        } else {
                            allOk = false;
                        }
                    }

                    if (allOk && !childReportList.isEmpty()) {
                        SafeFileOps.copyDirectory(covPathProperties.codeRootPath(), Paths.get(deployInfoEntity.getCodePath()).resolve("jacocoreport"), covPathProperties.reportRootPath(), reportTargetDir);
                        Integer[] result = MergeReportHtml.mergeHtml(childReportList, reportTargetDir.resolve("index.html").toString());

                        if (result[0] > 0) {
                            coverageReport.setReportUrl(LocalIpUtils.getTomcatBaseUrl() + coverageReport.getUuid() + "/index.html");
                            coverageReport.setRequestStatus(Constants.JobStatus.SUCCESS.val());

                            safeDeleteCodeDir(coverageReport);

                            Path resourceDirName = covPathProperties.jacocoResourceRootPath().getFileName();
                            if (resourceDirName != null) {
                                SafeFileOps.copyDirectory(covPathProperties.jacocoResourceRootPath(), covPathProperties.jacocoResourceRootPath(), covPathProperties.reportRootPath(), reportTargetDir.resolve(resourceDirName));
                            }

                            coverageReport.setLineCoverage(Double.valueOf(result[2]));
                            coverageReport.setBranchCoverage(Double.valueOf(result[1]));
                            return;
                        } else {
                            coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
                            coverageReport.setErrMsg("生成jacoco报告失败");
                        }
                    } else {
                        coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
                        coverageReport.setErrMsg("生成jacoco报告失败");
                    }
                }
            } else {
                coverageReport.setErrMsg("获取jacoco.exec 文件失败");
                coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
                log.error("uuid={}{}", coverageReport.getUuid(), coverageReport.getErrMsg());
                safeDeleteCodeDir(coverageReport);
            }
        } catch (java.util.concurrent.TimeoutException e) {
            coverageReport.setRequestStatus(Constants.JobStatus.TIMEOUT.val());
            coverageReport.setErrMsg("获取jacoco.exec 文件超时");
            log.error("uuid={}获取超时", coverageReport.getUuid(), e);
        } catch (Exception e) {
            coverageReport.setRequestStatus(Constants.JobStatus.ENVREPORT_FAIL.val());
            coverageReport.setErrMsg("获取jacoco.exec 文件发生未知错误");
            log.error("uuid={}获取jacoco.exec 文件发生未知错误", coverageReport.getUuid(), e);
        } finally {
            coverageReportDao.updateCoverageReportByReport(coverageReport);
        }
    }

    private void safeDeleteCodeDir(CoverageReportEntity coverageReport) throws IOException {
        Path now = Paths.get(coverageReport.getNowLocalPath()).toAbsolutePath().normalize();
        Path target = now.getParent();
        if (target == null) {
            return;
        }
        SafeFileOps.deleteRecursively(covPathProperties.codeRootPath(), target);
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

    private List<String> buildJacocoReportCmd(String execFile, List<String> modules, String diffFile, String reportName, String htmlDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(covPathProperties.getJacocoCliJar());
        cmd.add("report");
        cmd.add(execFile);
        if (modules == null || modules.isEmpty()) {
            cmd.add("--sourcefiles");
            cmd.add("./src/main/java/");
            cmd.add("--classfiles");
            cmd.add("./target/classes/com/");
        } else {
            for (String module : modules) {
                cmd.add("--sourcefiles");
                cmd.add("./" + module + "/src/main/java/");
                cmd.add("--classfiles");
                cmd.add("./" + module + "/target/classes/com/");
            }
        }
        if (!StringUtils.isEmpty(diffFile)) {
            cmd.add("--diffFile");
            cmd.add(diffFile);
        }
        cmd.add("--html");
        cmd.add(htmlDir);
        cmd.add("--encoding");
        cmd.add("utf-8");
        cmd.add("--name");
        cmd.add(reportName);
        return cmd;
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

        //1、计算增量代码
        String diffFiles = diffMethodsCalculator.executeDiffMethodsForEnv(basePath.toString(), nowPath.toString(), localHostRequestParam.getBaseVersion(), localHostRequestParam.getNowVersion());
        CoverResult result = new CoverResult();
        if (diffFiles == null) {
            result.setCoverStatus(-1);
            result.setLineCoverage(-1);
            result.setBranchCoverage(-1);
            result.setErrMsg("未检测到增量代码");
            return result;
        }
        //2、拉取jacoco.exec文件并解析
        if (StringUtils.isEmpty(localHostRequestParam.getAddress())) {
            localHostRequestParam.setAddress("127.0.0.1");
        }
        localHostRequestParam.setBasePath(basePath.toString());
        localHostRequestParam.setNowPath(nowPath.toString());
        CoverResult coverResult = pullExecFile(localHostRequestParam, diffFiles, localHostRequestParam.getSubModule());
        //3、tomcat整合
        //todo
        return coverResult;
    }

    /**
     * 拉取jacoco文件并转换为报告
     */
    private CoverResult pullExecFile(LocalHostRequestParam localHostRequestParam, String diffFiles, String subModule) {
        String reportName = "ManualDiffCoverage";
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
                //todo 删除原有报告
                // CmdExecutor.executeCmd(new String[]{"rm -rf " + REPORT_PATH + coverageReport.getUuid()}, CMD_TIMEOUT);

                List<String> modules = new ArrayList<>();
                if (!StringUtils.isEmpty(subModule)) {
                    modules.add(subModule);
                }
                List<String> reportCmd = buildJacocoReportCmd("./jacoco.exec", modules, diffFiles, reportName, "./jacocoreport/");
                int covExitCode = CmdExecutor.executeCmd(reportCmd, Paths.get(localHostRequestParam.getNowPath()), CMD_TIMEOUT, null);
                File reportFile = Paths.get(localHostRequestParam.getNowPath()).resolve("jacocoreport").resolve("index.html").toFile();

                if (covExitCode == 0 && reportFile.exists()) {
                    try {
                        // 解析并获取覆盖率
                        log.info("开始解析html元素");
                        Document doc = Jsoup.parse(reportFile.getAbsoluteFile(), "UTF-8", "");
                        Elements bars = doc.getElementById("coveragetable").getElementsByTag("tfoot").first().getElementsByClass("bar");
                        Elements lineCtr1 = doc.getElementById("coveragetable").getElementsByTag("tfoot").first().getElementsByClass("ctr1");
                        Elements lineCtr2 = doc.getElementById("coveragetable").getElementsByTag("tfoot").first().getElementsByClass("ctr2");
                        double lineCoverage = 100;
                        double branchCoverage = 100;
                        // 以上这里初始化都换成了1
                        if (doc != null && bars != null) {
                            float lineNumerator = Float.valueOf(lineCtr1.get(1).text().replace(",", ""));
                            float lineDenominator = Float.valueOf(lineCtr2.get(3).text().replace(",", ""));
                            log.info("lineNumerator={},lineDenominator={}", lineNumerator, lineDenominator);
                            lineCoverage = (lineDenominator - lineNumerator) / lineDenominator * 100;
                            String[] branch = bars.get(1).text().split(" of ");
                            float branchNumerator = Float.valueOf(branch[0].replace(",", ""));
                            float branchDenominator = Float.valueOf(branch[1].replace(",", ""));
                            log.info("branchNumerator={},branchDenominator={}", branchNumerator, branchDenominator);
                            if (branchDenominator > 0.0) {
                                branchCoverage = (branchDenominator - branchNumerator) / branchDenominator * 100;
                            }

                        }
                        coverResult.setCoverStatus(200);
                        coverResult.setLineCoverage(lineCoverage);
                        coverResult.setBranchCoverage(branchCoverage);
                        coverResult.setReportUrl(reportFile.getAbsolutePath());
                        return coverResult;
                        // todo 复制report报告

                    } catch (RuntimeException e) {
                        log.error("解析jacoco报告失败，msg={}", e.getMessage());
                        throw new RuntimeException("解析jacoco报告失败，msg=" + e.getMessage());
                    }
                } else {
                    // 可能不同子项目存在同一类名
                    if (StringUtils.isEmpty(subModule)) {
                        coverResult.setCoverStatus(-1);
                        coverResult.setErrMsg("拉取执行文件失败");
                        return coverResult;
                    }
                    ArrayList<String> childReportList = new ArrayList<>();

                    List<String> moduleReportCmd = buildJacocoReportCmd("./jacoco.exec", java.util.Collections.singletonList(subModule), diffFiles, reportName, "jacocoreport/" + subModule);
                    int moduleExitCode = CmdExecutor.executeCmd(moduleReportCmd, Paths.get(localHostRequestParam.getNowPath()), CMD_TIMEOUT, null);
                    if (moduleExitCode == 0) {
                        childReportList.add(Paths.get(localHostRequestParam.getNowPath()).resolve("jacocoreport").resolve(subModule).resolve("index.html").toString());
                    }

                    if (moduleExitCode == 0) {
                        // 合并
                        //todo 报告地址
                        Path targetDir = covPathProperties.localCovRootPath().resolve(localHostRequestParam.getUuid());
                        SafeFileOps.deleteRecursively(covPathProperties.localCovRootPath(), targetDir);
                        SafeFileOps.copyDirectory(covPathProperties.localCovRootPath(), Paths.get(localHostRequestParam.getNowPath()).resolve("jacocoreport"), covPathProperties.localCovRootPath(), targetDir.resolve("jacocoreport"));
                        Integer[] result = MergeReportHtml.mergeHtml(childReportList, targetDir.resolve("index.html").toString());

                        if (result[0] > 0) {
                            //todo 清理
                            coverResult.setCoverStatus(200);
                            coverResult.setLineCoverage(Double.valueOf(result[2]));
                            coverResult.setBranchCoverage(Double.valueOf(result[1]));
                            coverResult.setReportUrl(targetDir.resolve("index.html").toString());
                            return coverResult;
                        } else {
                            coverResult.setCoverStatus(-1);
                            coverResult.setErrMsg("拉取执行文件失败");
                            return coverResult;
                        }
                    } else {
                        // 生成报告错误
                        coverResult.setCoverStatus(-1);
                        coverResult.setErrMsg("拉取执行文件失败");
                        return coverResult;
                    }
                }
            } else {
                coverResult.setCoverStatus(-1);
                coverResult.setErrMsg("拉取执行文件失败");
                log.error("获取jacoco.exec 文件失败，uuid={}", localHostRequestParam.getUuid());
                return coverResult;
            }
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
