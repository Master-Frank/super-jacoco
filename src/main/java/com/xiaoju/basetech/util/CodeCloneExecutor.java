package com.xiaoju.basetech.util;

import com.xiaoju.basetech.entity.CoverageReportEntity;
import com.xiaoju.basetech.config.CovPathProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;



/**
 * @description:
 * @author: charlynegaoweiwei
 * @time: 2020/4/28 4:52 下午
 */
@Slf4j
@Component
public class CodeCloneExecutor {

    @Autowired
    private GitHandler gitHandler;

    @Autowired
    private CovPathProperties covPathProperties;

    public void cloneCode(CoverageReportEntity coverageReport) {
        InputValidator.requireSafeUuid(coverageReport.getUuid());
        InputValidator.requireSafeGitUrl(coverageReport.getGitUrl());
        InputValidator.requireSafeVersion(coverageReport.getNowVersion(), "nowVersion");
        if (!org.springframework.util.StringUtils.isEmpty(coverageReport.getBaseVersion())) {
            InputValidator.requireSafeVersion(coverageReport.getBaseVersion(), "baseVersion");
        }
        InputValidator.requireSafeSubModule(coverageReport.getSubModule());

        if (coverageReport.getType() == Constants.ReportType.DIFF.val() && coverageReport.getNowVersion().equals(coverageReport.getBaseVersion())) {
            coverageReport.setErrMsg("两个commitId一样，无增量代码");
            coverageReport.setRequestStatus(Constants.JobStatus.NODIFF.val());
            coverageReport.setReportUrl(Constants.NO_DIFFCODE_REPORT);
            coverageReport.setBranchCoverage((double) 100);
            coverageReport.setLineCoverage((double) 100);
            return;
        }
        String logFile = LocalIpUtils.getTomcatBaseUrl()+"logs/" + coverageReport.getUuid() + ".log";
        coverageReport.setLogFile(logFile);
        try {
            String uuid = coverageReport.getUuid();
            String codeRoot = covPathProperties.getCodeRoot();
            String nowLocalPath = codeRoot + uuid + "/" + coverageReport.getNowVersion().replace("/", "_");
            coverageReport.setNowLocalPath(nowLocalPath);
            if (new File(codeRoot + uuid + "/").exists()) {
                SafeFileOps.deleteRecursively(covPathProperties.codeRootPath(), Paths.get(codeRoot + uuid + "/"));
            }
            String gitUrl = coverageReport.getGitUrl();
            log.info("uuid {}开始下载代码...", uuid);

            Path localRepoPath = resolveLocalRepoPath(gitUrl);
            if (localRepoPath != null && Files.exists(localRepoPath) && Files.isDirectory(localRepoPath)) {
                if (coverageReport.getType() == Constants.ReportType.FULL.val()) {
                    Path normalizedLocalRepo = localRepoPath.toAbsolutePath().normalize();
                    Path normalizedNowTarget = Paths.get(nowLocalPath).toAbsolutePath().normalize();
                    SafeFileOps.copyDirectory(normalizedLocalRepo, normalizedLocalRepo, covPathProperties.codeRootPath(), normalizedNowTarget);
                    log.info("uuid {}完成下载代码...", uuid);
                    coverageReport.setRequestStatus(Constants.JobStatus.CLONE_DONE.val());
                    return;
                }
                if (!GitHandler.isValidGitRepository(localRepoPath.toString())) {
                    coverageReport.setErrMsg("gitUrl为本地路径时仅支持全量(type=1)，增量(type=2)需要git仓库");
                    coverageReport.setRequestStatus(Constants.JobStatus.CLONE_FAIL.val());
                    return;
                }
            }

            {
                gitHandler.cloneRepository(gitUrl, nowLocalPath, coverageReport.getNowVersion());
                if (coverageReport.getType() == Constants.ReportType.DIFF.val()) {
                    String baseLocalPath = codeRoot + uuid + "/" + coverageReport.getBaseVersion().replace("/", "_");
                    coverageReport.setBaseLocalPath(baseLocalPath);
                    gitHandler.cloneRepository(gitUrl, baseLocalPath, coverageReport.getBaseVersion());
                }
            }
            log.info("uuid {}完成下载代码...", uuid);
            coverageReport.setRequestStatus(Constants.JobStatus.CLONE_DONE.val());
        } catch (Exception e) {
            log.error("下载代码发生异常:{}", coverageReport.getUuid(), e);
            coverageReport.setErrMsg("下载代码发生异常:" + e.getMessage());
            coverageReport.setRequestStatus(Constants.JobStatus.CLONE_FAIL.val());
        }
    }

    private static Path resolveLocalRepoPath(String gitUrl) {
        if (org.springframework.util.StringUtils.isEmpty(gitUrl)) {
            return null;
        }
        if (gitUrl.startsWith("file:")) {
            try {
                return Paths.get(URI.create(gitUrl));
            } catch (Exception e) {
                return null;
            }
        }
        if (gitUrl.matches("^[A-Za-z]:[\\\\/].*") || gitUrl.startsWith("\\\\")) {
            try {
                return Paths.get(gitUrl);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

}
